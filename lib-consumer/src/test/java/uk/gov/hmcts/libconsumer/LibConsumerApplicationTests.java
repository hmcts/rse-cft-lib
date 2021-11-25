package uk.gov.hmcts.libconsumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;


import java.util.Collection;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.ImportController;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class LibConsumerApplicationTests {

	@Autowired
	ImportController controller;

	@Autowired
	UserRoleController roleController;

	@Autowired
	MockMvc mockMvc;

	@SneakyThrows
	MockMultipartFile loadNFDivDef() {

		return new MockMultipartFile(
				"file",
				"hello.txt",
				MediaType.MULTIPART_FORM_DATA_VALUE,
		        getClass().getClassLoader().getResourceAsStream("NFD-dev.xlsx").readAllBytes()
		);
	}

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
		Authentication authentication = Mockito.mock(Authentication.class);
		SecurityContext securityContext = Mockito.mock(SecurityContext.class);
		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);

		Mockito.when(authentication.getPrincipal()).thenReturn(Mockito.mock(Jwt.class));

		mockMvc.perform(multipart("/import").file(loadNFDivDef())
						.with(jwt()))
				.andExpect(status().is2xxSuccessful());
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
