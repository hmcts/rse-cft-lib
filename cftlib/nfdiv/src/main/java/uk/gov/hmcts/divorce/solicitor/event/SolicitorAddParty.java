package uk.gov.hmcts.divorce.solicitor.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Party;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Solicitor;
import uk.gov.hmcts.divorce.solicitor.service.CcdAccessService;
import uk.gov.hmcts.divorce.sow014.civil.PartyRepository;
import uk.gov.hmcts.reform.ccd.client.model.SubmittedCallbackResponse;

import java.util.Optional;

import static org.jooq.nfdiv.civil.Tables.PARTIES;
import static org.jooq.nfdiv.civil.Tables.SOLICITORS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.*;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

@Slf4j
@Component
public class SolicitorAddParty implements CCDConfig<CaseData, State, UserRole> {

    public static final String SOLICITOR_CREATE = "solicitor-add-party";

    @Autowired
    private CcdAccessService ccdAccessService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private DSLContext db;

    @Autowired
    private PartyRepository partyRepository;

    @Override
    public void configure(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        new PageBuilder(configBuilder
            .event(SOLICITOR_CREATE)
            .forAllStates()
            .name("Add Party Details")
            .description("Add Party Details")
            .aboutToSubmitCallback(this::aboutToSubmit)
            .showEventNotes()
            .grant(CREATE_READ_UPDATE,
                SOLICITOR, CASE_WORKER)
            .grant(CREATE_READ_UPDATE_DELETE,
                SUPER_USER)
            .grantHistoryOnly(LEGAL_ADVISOR, JUDGE))
            .page("addSolicitorPartyDetails")
            .pageLabel("Add solicitor party details")
            .complex(CaseData::getParty)
            .mandatoryWithLabel(Party::getForename, "Forename")
            .mandatoryWithLabel(Party::getSurname, "Lastname")
            .done()
        ;
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(CaseDetails<CaseData, State> details,
                                                                       CaseDetails<CaseData, State> beforeDetails) {
        log.info("Solicitor add party about to submit callback invoked for Case Id: {}", details.getId());

        CaseData data = details.getData();
        Party party = data.getParty();

        Optional<Solicitor> solicitors
            = db.select()
            .from(SOLICITORS)
            .where(SOLICITORS.ORGANISATION_ID.eq("10"))
            .and(SOLICITORS.REFERENCE.eq(details.getId()))
            .fetchInto(Solicitor.class)
            .stream().findFirst();

//        db.insertInto(PARTIES, PARTIES.REFERENCE, PARTIES.SOLICITOR_ID, PARTIES.FORENAME, PARTIES.SURNAME)
//            .values(
//                details.getId(),
//                Long.valueOf(solicitors.get().getSolicitorId()),
//                party.getForename(),
//                party.getSurname())
//            .execute();

        partyRepository.createPartyThroughCRUD(party, details.getId(), Long.valueOf(solicitors.get().getSolicitorId()));



        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }

    public SubmittedCallbackResponse submitted(CaseDetails<CaseData, State> details, CaseDetails<CaseData, State> before) {
        var orgId = details
            .getData()
            .getApplicant1()
            .getSolicitor()
            .getOrganisationPolicy()
            .getOrganisation()
            .getOrganisationId();

        log.info("Adding the applicant's solicitor case roles");
        ccdAccessService.addApplicant1SolicitorRole(
            httpServletRequest.getHeader(AUTHORIZATION),
            details.getId(),
            orgId
        );

        return SubmittedCallbackResponse.builder().build();
    }
}
