package uk.gov.hmcts.rse.ccd.lib.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonDefinitionReaderTest {

    private JsonDefinitionReader reader = new JsonDefinitionReader(new ObjectMapper());

    @Test
    public void readCaseType() {
        var result = reader.readPath("src/test/resources/definition/CaseType");

        assertEquals(result.get(0), Map.of(
            "Description", "Handling of the dissolution of marriage",
            "ID", "NFD",
            "JurisdictionID", "DIVORCE",
            "LiveFrom", "01/01/2017",
            "Name","New Law Case",
            "SecurityClassification", "Public"
        ));
    }

}