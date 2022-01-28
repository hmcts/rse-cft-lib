package uk.gov.hmcts.libconsumer;

import static org.junit.Assert.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.test.autoconfigure.web.servlet.SpringBootMockMvcBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.domain.model.aggregated.JurisdictionDisplayProperties;
import uk.gov.hmcts.rse.ccd.lib.Project;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.common.DBWaiter;
import uk.gov.hmcts.rse.ccd.lib.v2.am.BootAccessManagement;
import uk.gov.hmcts.rse.ccd.lib.v2.data.BootData;
import uk.gov.hmcts.rse.ccd.lib.v2.definition.BootDef;
import uk.gov.hmcts.rse.ccd.lib.v2.lib.ParentContextConfiguration;
import uk.gov.hmcts.rse.ccd.lib.v2.profile.BootUserProfile;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests {

  private Map<Project, ConfigurableApplicationContext> contexts = Maps.newConcurrentMap();

  Map<Project, List<Class>> childContexts = Map.of(
      Project.Application, List.of(LibConsumerApplication.class, CFTLib.class),
      Project.AM, List.of(BootAccessManagement.class),
      Project.Defstore, List.of(BootDef.class),
      Project.Userprofile, List.of(BootUserProfile.class),
      Project.Datastore, List.of(BootData.class)
  );

  Map<Project, MockMvc> mockMVCs = Maps.newHashMap();

  @SneakyThrows
  @BeforeAll
  void setup() {
    final SpringApplication parentApplication = new SpringApplication( ParentContextConfiguration.class, FakeS2S.class,
        DBWaiter.class);
    parentApplication.setWebApplicationType(WebApplicationType.NONE);
    var parentContext = parentApplication.run( "" );
    final ParentContextApplicationContextInitializer parentContextApplicationContextInitializer = new ParentContextApplicationContextInitializer( parentContext );

    childContexts.keySet().parallelStream().forEach(project -> {
      System.out.println("Starting " + project);
      var name = Thread.currentThread().getName();
        Thread.currentThread().setName("**** " + project);
        final SpringApplication a = new SpringApplication(childContexts.get(project).toArray(new Class[0]));
        a.addInitializers( parentContextApplicationContextInitializer );
        if (project == Project.Application) {
          final StandardEnvironment environment = new StandardEnvironment( );
          final Map<String, Object> properties = Map.of( "spring.autoconfigure.exclude",
              "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
          );
          environment.getPropertySources().addFirst( new MapPropertySource( "Autoconfig exclusions", properties ) );
          a.setEnvironment(environment);
        }
        var context = a.run();

        var builder = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context);
        new SpringBootMockMvcBuilderCustomizer((WebApplicationContext) context).customize(builder);

        mockMVCs.put(project, builder.apply(springSecurity())
            .build());
        contexts.put(project, context);
        Thread.currentThread().setName(name);
    });

    var userprofile = contexts.get(Project.Userprofile).getBean(UserProfileEndpoint.class);
    var roleController = contexts.get(Project.Defstore).getBean(UserRoleController.class);
    var lib = contexts.get(Project.Application).getBean(CFTLib.class);
    var amDB = contexts.get(Project.AM).getBean(DataSource.class);
    lib.init(roleController, userprofile, amDB);
  }

  @AfterAll
  void teardown() {
//    for (ConfigurableApplicationContext context : contexts) {
//      context.close();
//    }
  }

//  @SneakyThrows
//  @Test
//  void isHealthy() {
//    for (Project project : mockMVCs.keySet()) {
//      mockMVCs.get(project).perform(get("/health"))
//          .andExpect(status().is2xxSuccessful());
//    }
//  }

  @SneakyThrows
  @Test
  void addressLookup() {
    mockMVCs.get(Project.Datastore)
        .perform(get("/addresses"))
        .andExpect(status().is2xxSuccessful());
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
