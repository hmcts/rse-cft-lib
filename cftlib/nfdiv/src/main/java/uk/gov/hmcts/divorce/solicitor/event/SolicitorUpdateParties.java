package uk.gov.hmcts.divorce.solicitor.event;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.exception.DataChangedException;
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
import uk.gov.hmcts.divorce.sow014.lib.DynamicRadioListElement;
import uk.gov.hmcts.divorce.sow014.lib.MyRadioList;
import uk.gov.hmcts.divorce.sow014.lib.CaseUserRolesGetter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.jooq.nfdiv.civil.Tables.PARTIES;
import static org.jooq.nfdiv.civil.Tables.SOLICITORS;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.*;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

@Slf4j
@Component
public class SolicitorUpdateParties implements CCDConfig<CaseData, State, UserRole> {

    public static final String SOLICITOR_PARTY_UPDATE = "solicitor-update-parties";
    private static final String NEVER_SHOW = "Forename=\"never\"";

    private final DSLContext db;
    private final CaseUserRolesGetter caseUserRolesGetter;

    @Autowired
    public SolicitorUpdateParties(DSLContext db, CaseUserRolesGetter caseUserRolesGetter) {
        this.db = db;
        this.caseUserRolesGetter = caseUserRolesGetter;
    }

    @Override
    public void configure(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {

        final PageBuilder pageBuilder = new PageBuilder(configBuilder
            .event(SOLICITOR_PARTY_UPDATE)
            .forAllStates()
            .name("Update Party Details")
            .description("Update Party Details")
            .aboutToStartCallback(this::aboutToStart)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .showEventNotes()
            .grant(CREATE_READ_UPDATE,
                SOLICITOR, CASE_WORKER)
            .grant(CREATE_READ_UPDATE_DELETE,
                SUPER_USER)
            .grantHistoryOnly(LEGAL_ADVISOR, JUDGE));

        pageBuilder.page("selectParty", this::midEvent)
            .pageLabel("Select party from list")
            .mandatory(CaseData::getPartyNames);

        pageBuilder.page("updatePartyDetails")
            .pageLabel("Update solicitor party details")
            .complex(CaseData::getParty)
            .mandatoryWithLabel(Party::getForename, "Forename")
            .mandatoryWithLabel(Party::getSurname, "Lastname")
            .done();
    }

    @SneakyThrows
    public AboutToStartOrSubmitResponse<CaseData, State> aboutToStart(final CaseDetails<CaseData, State> details) {

        log.info("{} about to start callback invoked for Case Id: {}", SOLICITOR_PARTY_UPDATE, details.getId());

        final CaseData caseData = details.getData();

        var parties = getParties(details);

        if (parties != null) {


            if (!parties.stream()
                .map(party -> DynamicRadioListElement
                    .builder()
                    .label(party.getForename() + " - " + party.getSurname())
                    .code(party.getPartyId()).build())
                .collect(toList()).isEmpty()) {

                MyRadioList partyNamesList = MyRadioList
                    .builder()
                    .value(parties.stream()
                        .map(party -> DynamicRadioListElement
                            .builder()
                            .label(party.getForename() + " - " + party.getSurname())
                            .code(party.getPartyId()).build())
                        .collect(toList()).get(0))
                    .listItems(parties.stream()
                        .map(party1 -> DynamicRadioListElement
                            .builder()
                            .label(party1.getForename() + " - " + party1.getSurname())
                            .code(party1.getPartyId()).build())
                        .collect(toList()))
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

    private List<Party> getParties(CaseDetails<CaseData, State> details) {
        Set<String> userRoles = caseUserRolesGetter.getUserRoles(details.getId().toString(), caseUserRolesGetter.getUserId());
        Set<Long> SolicitorIds = db.fetch(SOLICITORS,
                SOLICITORS.ROLE.in(userRoles),
                SOLICITORS.REFERENCE.eq(details.getId()))
            .stream().map(s -> Long.valueOf(s.getSolicitorId())).collect(Collectors.toSet());

        if (!SolicitorIds.isEmpty()) {
            return db.select().from(PARTIES)
                .where(PARTIES.REFERENCE.eq(details.getId()))
                .and(PARTIES.SOLICITOR_ID.in(SolicitorIds))
                .fetchInto(Party.class);
        }
        return caseUserRolesGetter.isAdminCaseworker()
            ? db.select().from(PARTIES).where(PARTIES.REFERENCE.eq(details.getId())).fetchInto(Party.class)
            : null;
    }

    public AboutToStartOrSubmitResponse<CaseData, State> midEvent(
        CaseDetails<CaseData, State> details,
        CaseDetails<CaseData, State> detailsBefore
    ) {
        log.info("Mid-event callback triggered for howDoYouWantToApplyForDivorce");

        final CaseData data = details.getData();

        DynamicRadioListElement value = data.getPartyNames().getValue();
        String code = value.getCode();

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
                PARTIES.REFERENCE.eq(details.getId()));
            partiesRecord.setForename(party.getForename());
            partiesRecord.setSurname(party.getSurname());
            partiesRecord.setVersion(Long.valueOf(party.getVersion()));
            partiesRecord.store();
        } catch (DataChangedException de) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Database record has been changed or doesn't exist any longer");
        }

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }
}
