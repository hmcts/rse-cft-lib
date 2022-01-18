package uk.gov.hmcts.libconsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.gov.hmcts.ccd.domain.model.aggregated.JurisdictionDisplayProperties;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.rse.ccd.lib.FlywayMigrator;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  FlywayMigrator migrator;

  @SneakyThrows
  @Test
  void isHealthy() {
    mockMvc.perform(get("/health"))
        .andExpect(status().is2xxSuccessful());
  }

  @SneakyThrows
  @Test
  void listJurisdictions() {
    var r = mockMvc.perform(secure(get("/aggregated/caseworkers/:uid/jurisdictions?access=read"))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json"))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    var arr = new ObjectMapper().readValue(r.getResponse().getContentAsString(), JurisdictionDisplayProperties[].class);
    assertEquals(arr.length, 1);
    assertEquals(arr[0].getCaseTypeDefinitions().size(), 1);
  }

  @SneakyThrows
  @Test
  void getWorkbasketInputs() {
    var r = mockMvc.perform(secure(get("/data/internal/case-types/NFD/work-basket-inputs"))
            .header("Content-Type", "application/json")
            .header("experimental", "true")

        )
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  @Test
  void migrateExistingDB() {
    assertFalse(migrator.migrate());
  }

  @SneakyThrows
    // TODO
//  @Test
  void createNewCase() {
    StartEventResponse
        startEventResponse = startEventForCreateCase();

    CaseDataContent caseDataContent = CaseDataContent.builder()
        .eventToken(startEventResponse.getToken())
        .event(Event.builder()
            .id("solicitor-create-application")
            .summary("Create draft case")
            .description("Create draft case for functional tests")
            .build())
        .data(Map.of(
            "applicant1SolicitorName", "functional test",
            "applicant1LanguagePreferenceWelsh", "NO",
            "divorceOrDissolution", "divorce",
            "applicant1FinancialOrder", "NO"
        ))
        .build();

    mockMvc.perform(
            secure(post("/caseworkers/ham/jurisdictions/DIVORCE/case-types/NFD/cases"))
                .content(new ObjectMapper().writeValueAsString(caseDataContent))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  @SneakyThrows
  private StartEventResponse startEventForCreateCase() {
    MvcResult result = mockMvc.perform(
            secure(get("/caseworkers/ham/jurisdictions/DIVORCE/case-types/NFD/event-triggers/solicitor-create-application/token"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    return new ObjectMapper().readValue(result.getResponse().getContentAsString(), StartEventResponse.class);
  }


  MockHttpServletRequestBuilder secure(MockHttpServletRequestBuilder builder) {
    return builder.with(jwt()
        .authorities(new SimpleGrantedAuthority("caseworker-divorce-solicitor"))
        .jwt(this::buildJwt)).header("ServiceAuthorization", CFTLib.generateDummyS2SToken("ccd_gw"));
  }

  void buildJwt(Jwt.Builder builder) {
    builder.tokenValue(CFTLib.buildJwt())
        .subject("banderous");
  }

}
