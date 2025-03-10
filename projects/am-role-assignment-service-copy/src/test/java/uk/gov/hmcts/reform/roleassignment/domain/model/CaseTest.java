package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

import java.util.HashMap;

@RunWith(MockitoJUnitRunner.class)
class CaseTest {


    private final Case caseData = Case.builder().id("1234")
        .jurisdiction("IA")
        .caseTypeId("Asylum")
        .build();


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testToString() {
        assertNotNull(caseData.toString());
        assertTrue(caseData.toString().contains("1234"));
    }

    @Test
    void getRegionTest() {
        HashMap<String, JsonNode> regionMap = new HashMap<>();
        regionMap.put("region", convertValueJsonNode("London"));
        HashMap<String, JsonNode> caseDataMap = new HashMap<>();
        caseDataMap.put("caseManagementLocation", convertValueJsonNode(regionMap));

        caseData.setData(caseDataMap);
        assertEquals("London", caseData.getRegion());
    }

    @Test
    void getBaseLocationTest() {
        HashMap<String, JsonNode> regionMap = new HashMap<>();
        regionMap.put("baseLocation", convertValueJsonNode("London"));
        HashMap<String, JsonNode> caseDataMap = new HashMap<>();
        caseDataMap.put("caseManagementLocation", convertValueJsonNode(regionMap));

        caseData.setData(caseDataMap);
        assertEquals("London", caseData.getBaseLocation());
    }

}
