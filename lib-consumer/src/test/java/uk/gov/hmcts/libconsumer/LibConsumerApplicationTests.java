package uk.gov.hmcts.libconsumer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.ImportController;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class LibConsumerApplicationTests {

	@Autowired
	ImportController controller;

	@Autowired
	UserRoleController roleController;

	@Autowired
	WebApplicationContext context;

	MockMvc mockMvc;

	@Before
	public void setup() {
	}

	private static final String JURISDICTION = "DIVORCE";
	private static final String CASE_TYPE = "NFD";
	private static final String SOLICITOR_CREATE = "solicitor-create-application";

	@Autowired
	protected CoreCaseDataApi coreCaseDataApi;

	@SneakyThrows
	MockMultipartFile loadNFDivDef() {

		return new MockMultipartFile(
				"file",
				"hello.txt",
				MediaType.MULTIPART_FORM_DATA_VALUE,
		        getClass().getClassLoader().getResourceAsStream("NFD-dev.xlsx").readAllBytes()
		);
	}

//	@WithMockUser("goat")
	@SneakyThrows
	@Test
	void contextLoads() {
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
//		Authentication authentication = Mockito.mock(Authentication.class);
//		SecurityContext securityContext = Mockito.mock(SecurityContext.class);
//		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
//		SecurityContextHolder.setContext(securityContext);
//
//		Mockito.when(authentication.getPrincipal()).thenReturn(Mockito.mock(Jwt.class));
		mockMvc = MockMvcBuilders
				.webAppContextSetup(context)
				.apply(springSecurity())
				.build();

		mockMvc.perform(multipart("/import").file(loadNFDivDef())
						.with(jwt().authorities(new SimpleGrantedAuthority("caseworker-divorce-solicitor"))))
				.andExpect(status().is2xxSuccessful());


		String solicitorToken = "green";
		String s2sTokenForCaseApi = "eggs";
		String solicitorUserId = "ham";
		StartEventResponse
				startEventResponse = startEventForCreateCase(solicitorToken, s2sTokenForCaseApi, solicitorUserId);

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

		submitNewCase(caseDataContent, solicitorToken, s2sTokenForCaseApi, solicitorUserId);
	}

	@SneakyThrows
	private StartEventResponse startEventForCreateCase(
			String solicitorToken,
			String s2sToken,
			String solicitorUserId
	) {

		MvcResult result = mockMvc.perform(
						get("/caseworkers/ham/jurisdictions/DIVORCE/case-types/NFD/event-triggers/solicitor-create-application/token")
								.with(jwt().authorities(new SimpleGrantedAuthority("caseworker-divorce-solicitor")))
								.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().is2xxSuccessful())
				.andReturn();
		var r = new ObjectMapper().readValue(result.getResponse().getContentAsString(), StartEventResponse.class);
		return r;
//		coreCaseDataApi.startForCaseworker(
//				solicitorToken,
//				s2sToken,
//				solicitorUserId,
//				JURISDICTION,
//				CASE_TYPE,
//				SOLICITOR_CREATE
//		);
	}

	@SneakyThrows
	private void submitNewCase(
			CaseDataContent caseDataContent,
			String solicitorToken,
			String s2sToken,
			String solicitorUserId
	) {
		MvcResult result = mockMvc.perform(
						post("/caseworkers/ham/jurisdictions/DIVORCE/case-types/NFD/cases")
								.with(jwt().authorities(new SimpleGrantedAuthority("caseworker-divorce-solicitor")))
								.content(new ObjectMapper().writeValueAsString(caseDataContent))
								.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().is2xxSuccessful())
				.andReturn();

//		// not including in try catch to fast fail the method
//		return coreCaseDataApi.submitForCaseworker(
//				solicitorToken,
//				s2sToken,
//				solicitorUserId,
//				JURISDICTION,
//				CASE_TYPE,
//				true,
//				caseDataContent
//		);
	}

	void createRoles(String... roles) {
		for (String role : roles) {
			UserRole r = new UserRole();
			r.setRole(role);
			r.setSecurityClassification(SecurityClassification.PUBLIC);
			roleController.userRolePut(r);
		}

	}

}
