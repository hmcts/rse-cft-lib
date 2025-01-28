package uk.gov.hmcts.divorce.sow014.nfd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.divorce.caseworker.model.CaseNote;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;
import uk.gov.hmcts.divorce.sow014.lib.CaseRepository;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jooq.nfdiv.ccd.Tables.FAILED_JOBS;
import static org.jooq.nfdiv.civil.Civil.CIVIL;
import static org.jooq.nfdiv.public_.Tables.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Component
public class NFDCaseRepository implements CaseRepository {

    @Autowired
    private DSLContext db;

    @Autowired
    private ObjectMapper getMapper;

    @Autowired
    private PebbleEngine pebl;

    @Autowired
    private IdamService idamService;

    @Autowired
    private HttpServletRequest request;

    @SneakyThrows
    @Override
    public ObjectNode getCase(long caseRef, ObjectNode caseData) {
        var isLeadCase = db.fetchOptional(MULTIPLES, MULTIPLES.LEAD_CASE_ID.eq(caseRef));
        if (isLeadCase.isPresent()) {
            addLeadCaseInfo(caseRef, caseData);
        } else {
            caseData = addSubCaseInfo(caseRef, caseData);
        }

        var notes = loadNotes(caseRef);
        caseData.set("notes", getMapper.valueToTree(notes));

        caseData.put("markdownTabField", renderExampleTab(caseRef, notes));

        caseData.put("hyphenatedCaseRef", CaseData.formatCaseRef(caseRef));

        addAdminPanel(caseRef, caseData);

        addPendingApplications(caseRef, caseData);
        addClaims(caseRef, caseData);
        addSolicitorClaims(caseRef, caseData);

        return caseData;
    }

    @SneakyThrows
    private void addSolicitorClaims(long caseRef, ObjectNode caseData) {
        final User caseworkerUser = idamService.retrieveUser(request.getHeader(AUTHORIZATION));

        var clients = db.fetch(CIVIL.CLAIMS_BY_CLIENT,
            CIVIL.CLAIMS_BY_CLIENT.REFERENCE.eq(caseRef),
            CIVIL.CLAIMS_BY_CLIENT.SOLICITOR_ID.eq(caseworkerUser.getUserDetails().getUid())
            );

        PebbleTemplate compiledTemplate = pebl.getTemplate("yourClients");
        Writer writer = new StringWriter();

        Map<String, Object> context = new HashMap<>();
        context.put("clients", clients);

        compiledTemplate.evaluate(writer, context);
        caseData.put("clientsMd", writer.toString());
    }

    @SneakyThrows
    private void addClaims(long caseRef, ObjectNode caseData) {
        var claims = db.fetch(CIVIL.JUDGE_CLAIMS, CIVIL.JUDGE_CLAIMS.REFERENCE.eq(caseRef));

        PebbleTemplate compiledTemplate = pebl.getTemplate("claims");
        Writer writer = new StringWriter();

        Map<String, Object> context = new HashMap<>();
        context.put("claims", claims);

        compiledTemplate.evaluate(writer, context);
        caseData.put("claimsMd", writer.toString());
    }

    @SneakyThrows
    private void addPendingApplications(long caseRef, ObjectNode caseData) {
        var applications = db.select()
            .from(CIVIL.PENDING_APPLICATIONS);

        PebbleTemplate compiledTemplate = pebl.getTemplate("pendingApplications");
        Writer writer = new StringWriter();

        Map<String, Object> context = new HashMap<>();
        context.put("pendingApplications", applications);

        compiledTemplate.evaluate(writer, context);
        caseData.put("pendingApplicationsMd", writer.toString());

    }

    private void addAdminPanel(long caseRef, ObjectNode caseData) throws IOException {
        PebbleTemplate compiledTemplate = pebl.getTemplate("admin");
        Writer writer = new StringWriter();

        var failedJobs = db.fetch(FAILED_JOBS, FAILED_JOBS.REFERENCE.eq(caseRef));
        Map<String, Object> context = new HashMap<>();
        context.put("failedJobs", failedJobs);
        context.put("caseRef", caseRef);

        compiledTemplate.evaluate(writer, context);
        caseData.put("adminMd", writer.toString());
    }

    private void addLeadCaseInfo(long caseRef, ObjectNode caseData) throws IOException {
        // Fetch first 50
        var total = db.fetchCount(SUB_CASES, SUB_CASES.LEAD_CASE_ID.eq(caseRef));
        var subCases = db.selectFrom(SUB_CASES)
                .where(SUB_CASES.LEAD_CASE_ID.eq(caseRef))
                .limit(50)
                .fetch();
        if (subCases.isNotEmpty()) {
            caseData.put("leadCase", "Yes");

            PebbleTemplate compiledTemplate = pebl.getTemplate("subcases");
            Writer writer = new StringWriter();

            Map<String, Object> context = new HashMap<>();
            context.put("subcases", subCases);
            context.put("total", total);

            compiledTemplate.evaluate(writer, context);
            caseData.put("leadCaseMd", writer.toString());
        } else {
            caseData.put("leadCase", "No");
        }
    }

    private ObjectNode addSubCaseInfo(long caseRef, ObjectNode caseData) throws IOException {
        var leadCase = db.fetchOptional(SUB_CASES, SUB_CASES.SUB_CASE_ID.eq(caseRef));

        if (leadCase.isPresent()) {
            var derivedData = db.fetchOptional(DERIVED_CASES, DERIVED_CASES.SUB_CASE_ID.eq(caseRef));
            caseData = getMapper.readValue(derivedData.get().getData().data(), ObjectNode.class);
            caseData.put("leadCase", "No");

            PebbleTemplate compiledTemplate = pebl.getTemplate("leadcase");
            Writer writer = new StringWriter();

            Map<String, Object> context = new HashMap<>();
            context.put("leadCase", leadCase.get());

            compiledTemplate.evaluate(writer, context);
            caseData.put("subCaseMd", writer.toString());
        }
        return caseData;
    }


    private List<ListValue<CaseNote>> loadNotes(long caseRef) {
        return db.select()
           .from(CASE_NOTES)
           .where(CASE_NOTES.REFERENCE.eq(caseRef))
           .orderBy(CASE_NOTES.ID.desc())
           .fetchInto(CaseNote.class)
           .stream().map(n -> new ListValue<>(null, n))
           .toList();
    }

    @SneakyThrows
    private String renderExampleTab(long caseRef, List<ListValue<CaseNote>> notes) {
        PebbleTemplate compiledTemplate = pebl.getTemplate("notes");
        Writer writer = new StringWriter();

        long uptimeInSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        Map<String, Object> context = new HashMap<>();
        context.put("caseRef", caseRef);
        context.put("age", uptimeInSeconds);
        context.put("notes", notes);

        compiledTemplate.evaluate(writer, context);

        return writer.toString();
    }
}
