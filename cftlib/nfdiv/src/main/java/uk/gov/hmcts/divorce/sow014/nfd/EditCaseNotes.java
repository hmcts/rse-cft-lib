package uk.gov.hmcts.divorce.sow014.nfd;

import jakarta.servlet.http.HttpServletRequest;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.divorce.caseworker.model.CaseNote;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;

import java.time.Clock;
import java.time.LocalDate;

import static org.jooq.nfdiv.public_.Tables.CASE_NOTES;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.*;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.JUDGE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE_DELETE;

@Component
public class EditCaseNotes implements CCDConfig<CaseData, State, UserRole> {
    @Autowired
    private IdamService idamService;

    @Autowired
    private Clock clock;

    @Autowired
    @Lazy
    private DSLContext db;

    @Override
    public void configure(final ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        new PageBuilder(configBuilder
            .event("editCaseNotes")
            .forAllStates()
            .name("Edit case notes")
            .aboutToStartCallback(this::loadNotes)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .grant(CREATE_READ_UPDATE,
                CASE_WORKER)
            .grant(CREATE_READ_UPDATE_DELETE,
                SUPER_USER)
            .grantHistoryOnly(LEGAL_ADVISOR, JUDGE))
            .page("editCaseNotes")
            .pageLabel("Edit case notes")
            .mandatory(CaseData::getNotes);
    }

    private AboutToStartOrSubmitResponse<CaseData, State> loadNotes(CaseDetails<CaseData, State> caseDetails) {
        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(caseDetails.getData())
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(
        final CaseDetails<CaseData, State> details,
        final CaseDetails<CaseData, State> beforeDetails
    ) {
        var caseData = details.getData();

        var notes = caseData.getNotes().stream().map(ListValue::getValue).toList();
//        db.batchMerge(notes).execute();
        for (CaseNote note : notes) {
            var r = db.newRecord(CASE_NOTES, note);
            r.setReference(details.getId());
            r.merge();
        }


//        db.merge(CASE_NOTES)
//            .using(notes)
//            .execute();

//        db.mergeInto(CASE_NOTES)
//            .using(notes)
//            .execute();

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(caseData)
            .build();
    }
}

