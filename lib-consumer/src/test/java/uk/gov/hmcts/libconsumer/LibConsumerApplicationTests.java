package uk.gov.hmcts.libconsumer;

import java.util.Collection;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.ImportController;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;

@SpringBootTest
class LibConsumerApplicationTests {

	@Autowired
	ImportController controller;

	@Autowired
	UserRoleController roleController;

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

		controller.processUpload(loadNFDivDef());
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
