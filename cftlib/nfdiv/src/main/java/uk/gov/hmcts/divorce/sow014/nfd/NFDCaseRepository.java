package uk.gov.hmcts.divorce.sow014.nfd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.divorce.caseworker.model.CaseNote;
import uk.gov.hmcts.divorce.sow014.lib.CaseRepository;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jooq.nfdiv.public_.Tables.CASE_NOTES;

@Component
public class NFDCaseRepository implements CaseRepository {

    @Autowired
    private DSLContext db;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PebbleEngine pebl;

    @Override
    public ObjectNode getCase(long caseRef, ObjectNode caseData) {
        var notes = loadNotes(caseRef);
        caseData.set("notes", mapper.valueToTree(notes));
        caseData.put("markdownTabField", renderExampleTab(caseRef, notes));
        return caseData;
    }

    private List<ListValue<CaseNote>> loadNotes(long caseRef) {
        return db.select()
           .from(CASE_NOTES)
           .where(CASE_NOTES.REFERENCE.eq(caseRef))
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
