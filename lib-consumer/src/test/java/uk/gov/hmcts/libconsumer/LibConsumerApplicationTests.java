package uk.gov.hmcts.libconsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import java.sql.Connection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.ccd.domain.model.UserProfile;
import uk.gov.hmcts.ccd.domain.model.aggregated.JurisdictionDisplayProperties;
import uk.gov.hmcts.ccd.endpoint.userprofile.UserProfileEndpoint;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests {

  @Autowired
  UserRoleController roleController;

  @Autowired
  WebApplicationContext context;

  @Autowired
  UserProfileEndpoint userProfile;

  @Autowired
  ServiceAuthorisationApi s2s;

  @Autowired
  DataSource dataSource;

  @Autowired
  MockMvc mockMvc;

  @SneakyThrows
  @BeforeAll
  public void setup() {
    prepRoleAssignment();

    createProfile("a@b.com");
    createRoles(
        "caseworker-divorce-courtadmin_beta",
        "caseworker-divorce-superuser",
        "caseworker-divorce-courtadmin-la",
        "caseworker-divorce-courtadmin",
        "caseworker-divorce-solicitor",
        "caseworker-divorce-pcqextractor",
        "caseworker-divorce-systemupdate",
        "caseworker-divorce-bulkscan",
        "caseworker-caa",
        "citizen"
    );

    var f = new MockMultipartFile(
        "file",
        "hello.txt",
        MediaType.MULTIPART_FORM_DATA_VALUE,
        getClass().getClassLoader().getResourceAsStream("NFD-dev.xlsx").readAllBytes()
    );

    mockMvc.perform(secure(multipart("/import").file(f)))
        .andExpect(status().is2xxSuccessful());
  }

  @SneakyThrows
  void prepRoleAssignment() {
    try (Connection c = dataSource.getConnection()) {
      c.createStatement().execute(
          "create extension pgcrypto"
      );

      ResourceLoader resourceLoader = new DefaultResourceLoader();
      var json = IOUtils.toString(resourceLoader.getResource("classpath:am.json").getInputStream());
      var sql = IOUtils.toString(resourceLoader.getResource("classpath:populate_am.sql").getInputStream());
      var p = c.prepareStatement(sql);
      p.setString(1, json);
      p.executeQuery();
    }
  }

  MockHttpServletRequestBuilder secure(MockHttpServletRequestBuilder builder) {
    return builder.with(jwt()
        .authorities(new SimpleGrantedAuthority("caseworker-divorce-solicitor"))
        .jwt(this::buildJwt)).header("ServiceAuthorization", generateDummyS2SToken("ccd_gw"));
  }

  void buildJwt(Jwt.Builder builder) {
    var token = JWT.create()
        .withSubject("ronnie")
        .sign(Algorithm.HMAC256("a secret"));

    builder.tokenValue(token)
        .subject("ronnie");
  }

  public static String generateDummyS2SToken(String serviceName) {
    return Jwts.builder()
        .setSubject(serviceName)
        .setIssuedAt(new Date())
        .signWith(SignatureAlgorithm.HS256, TextCodec.BASE64.encode("AA"))
        .compact();

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

  void createRoles(String... roles) {
    for (String role : roles) {
      UserRole r = new UserRole();
      r.setRole(role);
      r.setSecurityClassification(SecurityClassification.PUBLIC);
      roleController.userRolePut(r);
    }
  }

  void createProfile(String id) {
    var p = new UserProfile();
    p.setId(id);
    p.setId(id);
    p.setWorkBasketDefaultJurisdiction("DIVORCE");
    p.setWorkBasketDefaultCaseType("NO_FAULT_DIVORCE");
    p.setWorkBasketDefaultState("Submitted");
    userProfile.populateUserProfiles(List.of(p), "banderous");
  }
}
