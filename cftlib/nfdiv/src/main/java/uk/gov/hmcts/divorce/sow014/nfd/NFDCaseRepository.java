package uk.gov.hmcts.divorce.sow014.nfd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.type.ListValue;
import uk.gov.hmcts.divorce.caseworker.model.CaseNote;
import uk.gov.hmcts.divorce.sow014.lib.CaseRepository;

@Component
public class NFDCaseRepository implements CaseRepository {

    @Autowired
    private JdbcTemplate db;
    @Autowired
    private ObjectMapper mapper;

    @Override
    public ObjectNode getCase(long caseRef, ObjectNode caseData) {
        var notes = db.query("""
                select date, note, author from case_notes where reference = ?
                """,
            new BeanPropertyRowMapper<>(CaseNote.class),
            caseRef);
        var noteList = notes.stream().map(x -> ListValue.builder().value(x).build()).toList();
        caseData.set("notes", mapper.valueToTree(noteList));
        return caseData;
    }
}
