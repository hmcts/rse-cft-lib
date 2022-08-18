package uk.gov.hmcts.rse.ccd.lib.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaseTypeRepositoryTest {

    private static final Map<String, String> paths = Map.of(
        "NFD", "src/test/resources/definition"
    );

    private static CaseType caseType;

    @BeforeAll
    public static void setup() {
        var repository = new CaseTypeRepository(paths, new JsonDefinitionReader(new ObjectMapper()));

        caseType = repository.findByCaseTypeId("NFD").orElseThrow();
    }

    @Test
    void setsCaseTypeDetails() {
        assertEquals("NFD", caseType.getId());
        assertEquals("New Law Case", caseType.getName());
        assertEquals("Handling of the dissolution of marriage", caseType.getDescription());
        assertEquals("DIVORCE", caseType.getJurisdiction().getId());
    }

    @Test
    void setsCaseFields() {
        var fields = caseType.getCaseFields();
        assertEquals(585, fields.size());
    }
}