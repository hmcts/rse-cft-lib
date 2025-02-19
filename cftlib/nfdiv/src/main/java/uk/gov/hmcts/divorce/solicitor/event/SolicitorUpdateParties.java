package uk.gov.hmcts.divorce.solicitor.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
import uk.gov.hmcts.divorce.solicitor.service.CcdAccessService;
import uk.gov.hmcts.divorce.sow014.civil.PartyRepository;
import uk.gov.hmcts.divorce.sow014.lib.DynamicRadioListElement;
import uk.gov.hmcts.divorce.sow014.lib.MyRadioList;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.CASE_WORKER;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.JUDGE;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.LEGAL_ADVISOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.SOLICITOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.SUPER_USER;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

@Slf4j
@Component
public class SolicitorUpdateParties implements CCDConfig<CaseData, State, UserRole> {

    public static final String SOLICITOR_PARTY_UPDATE = "solicitor-update-parties";
    private static final String NEVER_SHOW = "Forename=\"never\"";

    private CcdAccessService ccdAccessService;

    private HttpServletRequest httpServletRequest;
    private final PartyRepository partyRepository;

    @Autowired
    public SolicitorUpdateParties(CcdAccessService ccdAccessService, HttpServletRequest httpServletRequest,
                                  PartyRepository partyRepository) {
        this.ccdAccessService = ccdAccessService;
        this.httpServletRequest = httpServletRequest;
        this.partyRepository = partyRepository;
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

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToStart(final CaseDetails<CaseData, State> details) {

        log.info("{} about to start callback invoked for Case Id: {}", SOLICITOR_PARTY_UPDATE, details.getId());

        final CaseData caseData = details.getData();
        List<DynamicRadioListElement> partyNames =
            caseData.getParties()
                .stream()
                .map(party ->
                    DynamicRadioListElement
                        .builder()
                        .label(party.getValue().getForename() + " - " + party.getValue().getSurname())
                        .code(party.getValue().getPartyId()).build()
                )
                .collect(toList());

        MyRadioList partyNamesList = MyRadioList
            .builder()
            .value(partyNames.get(0))
            .listItems(partyNames)
            .build();

        caseData.setPartyNames(partyNamesList);

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(caseData)
            .errors(null)
            .warnings(null)
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> midEvent(
        CaseDetails<CaseData, State> details,
        CaseDetails<CaseData, State> detailsBefore
    ) {
        log.info("Mid-event callback triggered for howDoYouWantToApplyForDivorce");

        final CaseData data = details.getData();

        DynamicRadioListElement value = data.getPartyNames().getValue();
        String code = value.getCode();
        data.setParty(partyRepository.fetchById(Integer.parseInt(code)));

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(CaseDetails<CaseData, State> details,
                                                                       CaseDetails<CaseData, State> beforeDetails) {
        log.info("Solicitor add party about to submit callback invoked for Case Id: {}", details.getId());

        CaseData data = details.getData();
        Party party = data.getParty();

        partyRepository.updatePartyThroughCRUD(party);
//        Party existingParty = partyRepository.fetchById(Integer.parseInt(party.getPartyId()));
//
//        if (existingParty != null && existingParty.getVersion().equals(party.getVersion())) {
//            partyRepository.updateParty(party);
//        } else {
//            throw new ResponseStatusException(HttpStatus.CONFLICT, "Party record has been changed or doesn't exist any longer.");
//        }

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }
}
