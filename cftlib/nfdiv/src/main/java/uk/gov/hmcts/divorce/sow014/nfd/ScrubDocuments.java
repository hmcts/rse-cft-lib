package uk.gov.hmcts.divorce.sow014.nfd;

import static org.jooq.nfdiv.ccd.Tables.FAILED_JOBS;
import static org.jooq.nfdiv.public_.Tables.MULTIPLES;
import static org.jooq.nfdiv.public_.Tables.SUB_CASES;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.*;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.JUDGE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.nfdiv.public_.tables.records.SubCasesRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.document.model.DivorceDocument;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;
import uk.gov.hmcts.divorce.sow014.lib.CCDApi;
import uk.gov.hmcts.divorce.sow014.lib.MyRadioList;
import uk.gov.hmcts.divorce.sow014.lib.DynamicRadioListElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@Slf4j
public class ScrubDocuments implements CCDConfig<CaseData, State, UserRole> {
    @Autowired
    @Lazy
    private DSLContext db;

    @Autowired
    private CCDApi ccdApi;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private IdamService idamService;


    @Override
    public void configure(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        new PageBuilder(configBuilder
            .event("scrubDocuments")
            .forAllStates()
            .name("Admin - erase documents")
            .aboutToStartCallback(this::aboutToStart)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .showEventNotes()
            .grant(CREATE_READ_UPDATE,
                CASE_WORKER)
            .grant(CREATE_READ_UPDATE_DELETE,
                SUPER_USER)
            .grantHistoryOnly(LEGAL_ADVISOR, JUDGE))
            .page("scrubDocuments")
            .pageLabel("Choose the document")
            .mandatory(CaseData::getScrubbableDocs)
            .build();
    }

    private AboutToStartOrSubmitResponse<CaseData, State> aboutToStart(CaseDetails<CaseData, State> details) {
        var choices = new ArrayList<DynamicRadioListElement>();
        for (ListValue<DivorceDocument> d : details.getData().getDocuments().getDocumentsUploaded()) {
            choices.add(DynamicRadioListElement.builder()
                .code(d.getId())
                .label(d.getValue().getDocumentFileName())
                .build());
        }

        MyRadioList radioList = MyRadioList.builder()
            .value(choices.get(0))
            .listItems(choices)
            .build();

        details.getData().setScrubbableDocs(radioList);
        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(details.getData())
            .build();
    }


    @SneakyThrows
    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(
        final CaseDetails<CaseData, State> details,
        final CaseDetails<CaseData, State> beforeDetails
    ) {

        details.getData().getDocuments().getDocumentsUploaded().removeIf(
            d -> d.getId().equals(details.getData().getScrubbableDocs().getValue().getCode())
        );
        final User user = idamService.retrieveUser(request.getHeader(AUTHORIZATION));
        ccdApi.scrubHistory(details.getId(), user.getUserDetails().getUid(), event -> {
            var h = mapper.readValue(event, CaseData.class);
            if (h.getDocuments().getDocumentsUploaded() != null) {
                h.getDocuments().getDocumentsUploaded().removeIf(
                    d -> d.getId().equals(details.getData().getScrubbableDocs().getValue().getCode())
                );
            }
            return mapper.writeValueAsString(h);
        });

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(details.getData())
            .build();
    }
}
