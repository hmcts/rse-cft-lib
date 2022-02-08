package uk.gov.hmcts.libconsumer;

import static org.junit.Assert.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.gov.hmcts.ccd.domain.model.aggregated.JurisdictionDisplayProperties;
import uk.gov.hmcts.rse.ccd.lib.impl.Project;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;
import uk.gov.hmcts.rse.ccd.lib.test.CftLibTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests extends CftLibTest {

  @Override
  @BeforeAll
  protected void setup() {
    // Should override application.properties
    System.setProperty("ccd.defaultPrintName", "overridden");
    super.setup();
  }

  @Override
  protected Class getApplicationClass() {
    return LibConsumerApplication.class;
  }

  @SneakyThrows
  @Test
  void isHealthy() {
    for (Project project : mockMVCs.keySet()) {
      if (project != Project.Application && project != Project.AM) {
        mockMVCs.get(project).perform(get("/health"))
            .andExpect(status().is2xxSuccessful());
      }
    }
  }

  @SneakyThrows
  @Test
  void addressLookup() {
    mockMVCs.get(Project.Datastore)
        .perform(get("/addresses"))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  void systemPropertiesAndEnvVarsTakePrecedence() {
    var prop = contexts.get(Project.Datastore).getEnvironment().getProperty("ccd.defaultPrintName");
    // Should be overridden by system property we set.
    Assertions.assertEquals("overridden", prop);
  }

  @SneakyThrows
  @Test
  void listJurisdictions() {
    var mockMvc = mockMVCs.get(Project.Datastore);
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
    var mockMvc = mockMVCs.get(Project.Datastore);
    var r = mockMvc.perform(secure(get("/data/internal/case-types/NFD/work-basket-inputs"))
            .header("Content-Type", "application/json")
            .header("experimental", "true")

        )
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

//  @SneakyThrows
//    // TODO
////  @Test
//  void createNewCase() {
//    StartEventResponse
//        startEventResponse = startEventForCreateCase();
//
//    CaseDataContent caseDataContent = CaseDataContent.builder()
//        .eventToken(startEventResponse.getToken())
//        .event(Event.builder()
//            .id("solicitor-create-application")
//            .summary("Create draft case")
//            .description("Create draft case for functional tests")
//            .build())
//        .data(Map.of(
//            "applicant1SolicitorName", "functional test",
//            "applicant1LanguagePreferenceWelsh", "NO",
//            "divorceOrDissolution", "divorce",
//            "applicant1FinancialOrder", "NO"
//        ))
//        .build();
//
//    mockMvc.perform(
//            secure(post("/caseworkers/ham/jurisdictions/DIVORCE/case-types/NFD/cases"))
//                .content(new ObjectMapper().writeValueAsString(caseDataContent))
//                .contentType(MediaType.APPLICATION_JSON))
//        .andExpect(status().is2xxSuccessful())
//        .andReturn();
//  }
//
//  @SneakyThrows
//  private StartEventResponse startEventForCreateCase() {
//    MvcResult result = mockMvc.perform(
//            secure(get("/caseworkers/ham/jurisdictions/DIVORCE/case-types/NFD/event-triggers/solicitor-create-application/token"))
//                .contentType(MediaType.APPLICATION_JSON))
//        .andExpect(status().is2xxSuccessful())
//        .andReturn();
//    return new ObjectMapper().readValue(result.getResponse().getContentAsString(), StartEventResponse.class);
//  }
//
//
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
