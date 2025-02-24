package uk.gov.hmcts.divorce.solicitor.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.exception.DataChangedException;
import org.jooq.exception.NoDataFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Party;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;
import uk.gov.hmcts.divorce.solicitor.service.CcdAccessService;
import uk.gov.hmcts.divorce.sow014.lib.DynamicRadioListElement;
import uk.gov.hmcts.divorce.sow014.lib.MyRadioList;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.jooq.nfdiv.civil.Tables.PARTIES;
import static org.jooq.nfdiv.civil.Tables.SOLICITORS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.*;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

@Slf4j
@Component
public class SolicitorConcurrentUpdateParties implements CCDConfig<CaseData, State, UserRole> {

    public static final String SOLICITOR_PARTY_UPDATE = "concurrent-update-parties";
    private static final String NEVER_SHOW = "Forename=\"never\"";

    private CcdAccessService ccdAccessService;

    private HttpServletRequest httpServletRequest;
    private final IdamService idamService;
    private final DSLContext db;

    @Autowired
    public SolicitorConcurrentUpdateParties(CcdAccessService ccdAccessService, HttpServletRequest httpServletRequest,
                                            IdamService idamService, DSLContext db) {
        this.ccdAccessService = ccdAccessService;
        this.httpServletRequest = httpServletRequest;
        this.idamService = idamService;
        this.db = db;
    }

    @Override
    public void configure(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {

        final PageBuilder pageBuilder = new PageBuilder(configBuilder
            .event(SOLICITOR_PARTY_UPDATE)
            .forAllStates()
            .name("Update Party Concurrently")
            .description("Update Party Details Concurrently")
            .aboutToStartCallback(this::aboutToStart)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .showEventNotes()
            .grant(CREATE_READ_UPDATE,
                SOLICITOR, CASE_WORKER)
            .grant(CREATE_READ_UPDATE_DELETE,
                SUPER_USER)
            .grantHistoryOnly(LEGAL_ADVISOR, JUDGE));

        pageBuilder.page("selectPartyForConcurrentUpdate", this::midEvent)
            .pageLabel("Select party from list")
            .mandatory(CaseData::getPartyNames);

        pageBuilder.page("updatePartyDetailsConcurrently")
            .pageLabel("Update solicitor party details concurrently")
            .complex(CaseData::getParty)
            .mandatoryWithLabel(Party::getForename, "Forename")
            .mandatoryWithLabel(Party::getSurname, "Lastname")
            .done();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToStart(final CaseDetails<CaseData, State> details) {

        log.info("{} about to start callback invoked for Case Id: {}", SOLICITOR_PARTY_UPDATE, details.getId());

        SolicitorOrgDetails solicitorOrgDetails = SolicitorOrgDetails.from(getUserId()).get();
        final CaseData caseData = details.getData();

        Set<Long> SolicitorIds = db.fetch(SOLICITORS,
                SOLICITORS.ORGANISATION_ID.eq(solicitorOrgDetails.getOrganisationId()),
                SOLICITORS.REFERENCE.eq(details.getId()))
            .stream().map(s -> Long.valueOf(s.getSolicitorId())).collect(Collectors.toSet());

        if (!SolicitorIds.isEmpty()) {
            List<Party> parties = db.select()
                .from(PARTIES)
                .where(PARTIES.REFERENCE.eq(details.getId()))
                .and(PARTIES.SOLICITOR_ID.in(SolicitorIds))
                .and(PARTIES.LOCKED_AT.isNull().or(PARTIES.LOCKED_AT.lessThan(LocalDateTime.now())))
                .fetchInto(Party.class);
            ;

            List<DynamicRadioListElement> partyNames = parties.stream()
                    .map(party -> DynamicRadioListElement
                            .builder()
                            .label(party.getForename() + " - " + party.getSurname())
                            .code(party.getPartyId()).build())
                    .collect(toList());

            if (!partyNames.isEmpty()) {

                MyRadioList partyNamesList = MyRadioList
                        .builder()
                        .value(partyNames.get(0))
                        .listItems(partyNames)
                        .build();

                caseData.setPartyNames(partyNamesList);
            }
        }
        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(caseData)
            .errors(null)
            .warnings(null)
            .build();
    }

    private String getUserId() {
        var auth = httpServletRequest.getHeader(AUTHORIZATION);
        User solicitorUser = idamService.retrieveUser(auth);
        String userId = solicitorUser.getUserDetails().getUid();
        return userId;
    }

    public AboutToStartOrSubmitResponse<CaseData, State> midEvent(
        CaseDetails<CaseData, State> details,
        CaseDetails<CaseData, State> detailsBefore
    ) {
        log.info("Mid-event callback triggered for howDoYouWantToApplyForDivorce");

        final CaseData data = details.getData();

        DynamicRadioListElement value = data.getPartyNames().getValue();
        String code = value.getCode();

        var partiesRecord = db.fetchSingle(PARTIES,
            PARTIES.PARTY_ID.eq(Integer.parseInt(code)),
            PARTIES.REFERENCE.eq(details.getId()));
        partiesRecord.setLockedAt(LocalDateTime.now().plusMinutes(2));
        partiesRecord.setLockedBy(getUserId());
        partiesRecord.store();

        Party party = db.selectFrom(PARTIES)
            .where(PARTIES.PARTY_ID.eq(Integer.parseInt(code)))
            .and(PARTIES.REFERENCE.eq(details.getId()))
            .forUpdate()
            .fetchOneInto(Party.class);


        data.setParty(party);

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(CaseDetails<CaseData, State> details,
                                                                       CaseDetails<CaseData, State> beforeDetails) {
        log.info("Solicitor add party about to submit callback invoked for Case Id: {}", details.getId());

        CaseData data = details.getData();
        Party party = data.getParty();

        try {

            var partiesRecord = db.fetchSingle(PARTIES,
                PARTIES.PARTY_ID.eq(Integer.valueOf(party.getPartyId())),
                PARTIES.REFERENCE.eq(details.getId()),
                PARTIES.LOCKED_BY.eq(getUserId()));
            partiesRecord.setForename(party.getForename());
            partiesRecord.setSurname(party.getSurname());
            partiesRecord.setVersion(Long.valueOf(party.getVersion()));
            partiesRecord.setLockedAt(null);
            partiesRecord.setLockedBy(null);
            partiesRecord.store();
        } catch (DataChangedException | NoDataFoundException de) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Database record has been changed or doesn't exist any longer");
        }

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }
}
