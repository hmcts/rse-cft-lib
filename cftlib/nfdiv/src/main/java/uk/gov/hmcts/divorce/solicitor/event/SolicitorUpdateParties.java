package uk.gov.hmcts.divorce.solicitor.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.Event;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.DynamicList;
import uk.gov.hmcts.ccd.sdk.type.DynamicListElement;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.ccd.sdk.type.OrderSummary;
import uk.gov.hmcts.divorce.common.AddSystemUpdateRole;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Party;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Solicitor;
import uk.gov.hmcts.divorce.solicitor.service.CcdAccessService;
import uk.gov.hmcts.divorce.solicitor.service.SolicitorCreateApplicationService;
import uk.gov.hmcts.divorce.sow014.lib.DynamicRadioListElement;
import uk.gov.hmcts.divorce.sow014.lib.MyRadioList;
import uk.gov.hmcts.reform.ccd.client.model.SubmittedCallbackResponse;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.jooq.nfdiv.civil.Tables.PARTIES;
import static org.jooq.nfdiv.civil.Tables.SOLICITORS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.ccd.sdk.type.YesOrNo.NO;
import static uk.gov.hmcts.divorce.divorcecase.model.SupplementaryCaseType.NA;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.*;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;
import static uk.gov.hmcts.divorce.payment.PaymentService.*;

@Slf4j
@Component
public class SolicitorUpdateParties implements CCDConfig<CaseData, State, UserRole> {

    public static final String SOLICITOR_PARTY_UPDATE = "solicitor-update-parties";
    private static final String NEVER_SHOW = "Forename=\"never\"";

    @Autowired
    private CcdAccessService ccdAccessService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private DSLContext db;

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

//        Optional<Party> party
//            = db.select()
//            .from(PARTIES)
//            .where(PARTIES.FORENAME.eq(names[0]))
//            .and(PARTIES.SURNAME.eq(names[1]))
//            .and(PARTIES.REFERENCE.eq(details.getId()))
//            .fetchInto(Party.class)
//            .stream().findFirst();
        String[] strings = value.getLabel().split(" - ");
        data.setParty(Party.builder().partyId(code).forename(strings[0]).surname(strings[1]).build());

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(CaseDetails<CaseData, State> details,
                                                                       CaseDetails<CaseData, State> beforeDetails) {
        log.info("Solicitor add party about to submit callback invoked for Case Id: {}", details.getId());

        CaseData data = details.getData();
        Party party = data.getParty();

        db.update(PARTIES)
            .set(PARTIES.FORENAME, party.getForename())
            .set(PARTIES.SURNAME, party.getSurname())
            .where(PARTIES.PARTY_ID.eq(Integer.valueOf(party.getPartyId())))
            .execute();

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(data)
            .build();
    }
}
