package uk.gov.hmcts.rse.ccd.lib.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonDefinitionReaderTest {

    private JsonDefinitionReader reader = new JsonDefinitionReader(new ObjectMapper());

    @Test
    public void readsSingleFile() {
        var result = reader.readPath("src/test/resources/definition/CaseType");

        assertEquals(Map.of(
            "Description", "Handling of the dissolution of marriage",
            "ID", "NFD",
            "JurisdictionID", "DIVORCE",
            "LiveFrom", "01/01/2017",
            "Name","New Law Case",
            "SecurityClassification", "Public"
        ), result.get(0));
    }

    @Test
    public void readsFileAndDirectory() {
        var result = reader.readPath("src/test/resources/definition/AuthorisationCaseType");

        assertEquals("[SOLICITOR]", result.get(0).get("UserRole"));
        assertEquals("[CITIZEN]", result.get(1).get("UserRole"));
        assertEquals("[APPLICANTTWO]", result.get(2).get("UserRole"));
        assertEquals("[APPONESOLICITOR]", result.get(3).get("UserRole"));
    }

}