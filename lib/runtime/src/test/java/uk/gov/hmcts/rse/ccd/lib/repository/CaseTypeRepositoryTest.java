package uk.gov.hmcts.rse.ccd.lib.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaseTypeRepositoryTest {

    private final Map<String, String> paths = Map.of(
        "NFD", "src/test/resources/definition"
    );

    private CaseTypeRepository repository = new CaseTypeRepository(paths, new JsonDefinitionReader(new ObjectMapper()));

    @Test
    void findByCaseTypeId() {
        var caseType = repository.findByCaseTypeId("NFD").orElseThrow();

        assertEquals("NFD", caseType.getId());
    }
}