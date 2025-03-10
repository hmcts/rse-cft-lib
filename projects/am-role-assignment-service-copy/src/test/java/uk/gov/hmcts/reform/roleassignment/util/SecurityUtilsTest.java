package uk.gov.hmcts.reform.roleassignment.util;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.oidc.IdamRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION;

class SecurityUtilsTest {

    @Mock
    private final AuthTokenGenerator authTokenGenerator = mock(AuthTokenGenerator.class);

    private final IdamRepository idamRepositoryMock = mock(IdamRepository.class);

    @Mock
    Authentication authentication = Mockito.mock(Authentication.class);

    @Mock
    SecurityContext securityContext = mock(SecurityContext.class);

    @InjectMocks
    private final SecurityUtils securityUtils = new SecurityUtils(
        authTokenGenerator,
        idamRepositoryMock
    );

    private static final String USER_JWT = "Bearer 8gf364fg367f67";

    private final String serviceAuthorization = "Bearer eyJhbGciOiJIUzUxMiJ9"
        + ".eyJzdWIiOiJjY2RfZ3ciLCJleHAiOjE1OTQ2ODQ5MTF9"
        + ".LH3aiNniHNMlTwuSdzgRic9sD_4inQv5oUqJ0kkRKVasS4RfhIz2tRdttf-sSMkUga1p1teOt2iCq4BQBDS7KA";
    private final String serviceAuthorizationNoBearer = "eyJhbGciOiJIUzUxMiJ9"
        + ".eyJzdWIiOiJjY2RfZ3ciLCJleHAiOjE1OTQ2ODQ5MTF9"
        + ".LH3aiNniHNMlTwuSdzgRic9sD_4inQv5oUqJ0kkRKVasS4RfhIz2tRdttf-sSMkUga1p1teOt2iCq4BQBDS7KA";
    private static final String USER_ID = "21334a2b-79ce-44eb-9168-2d49a744be9c";



    private void mockSecurityContextData() throws IOException {
        List<String> collection = new ArrayList<>();
        collection.add("string");
        Map<String, Object> headers = new HashMap<>();
        headers.put("header", "head");
        Jwt jwt =   Jwt.withTokenValue(USER_JWT)
            .claim("aClaim", "aClaim")
            .claim("aud", Lists.newArrayList("ccd_gateway"))
            .header("aHeader", "aHeader")
            .build();
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication().getPrincipal()).thenReturn(jwt);
        when(idamRepositoryMock.getUserInfo(USER_JWT)).thenReturn(TestDataBuilder.buildUserInfo(USER_ID));
        when(authTokenGenerator.generate()).thenReturn(serviceAuthorization);
    }

    @BeforeEach
    public void setUp() throws IOException {
        mockSecurityContextData();
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getUserId() {
        assertEquals(USER_ID, securityUtils.getUserId());
    }

    @Test
    void getUserRolesHeader() {
        assertNotNull(securityUtils.getUserRolesHeader());
    }

    @Test
    void getUserToken() {
        String result = securityUtils.getUserToken();
        assertNotNull(result);
        assertTrue(result.contains(USER_JWT));
    }

    @Test
    void getAuthorizationHeaders() {
        HttpHeaders result = securityUtils.authorizationHeaders();
        assertEquals(serviceAuthorization, Objects.requireNonNull(result.get(SERVICE_AUTHORIZATION)).get(0));
        assertEquals(USER_ID, Objects.requireNonNull(result.get("user-id")).get(0));
        assertEquals("", Objects.requireNonNull(Objects.requireNonNull(result.get("user-roles")).get(0)));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(USER_JWT, securityUtils.getUserToken());
    }

    @Test
    void getAuthorizationHeaders_NoContext() {
        when(securityContext.getAuthentication()).thenReturn(null);
        HttpHeaders result = securityUtils.authorizationHeaders();
        assertEquals(serviceAuthorization, Objects.requireNonNull(result.get(SERVICE_AUTHORIZATION)).get(0));
        assertEquals(USER_ID, Objects.requireNonNull(result.get("user-id")).get(0));
        assertEquals("", Objects.requireNonNull(Objects.requireNonNull(result.get("user-roles")).get(0)));
        assertNotNull(result.get(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void getServiceAuthorizationHeader() {
        when(authTokenGenerator.generate()).thenReturn("Hello");
        final String authHeader = securityUtils.getServiceAuthorizationHeader();
        assertFalse(authHeader.isBlank());
    }

    @Test
    void removeBearerFromToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SERVICE_AUTHORIZATION, serviceAuthorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals("ccd_gw", securityUtils.getServiceName());
    }

    @Test
    void removeBearerFromToken_NoBearerTag() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SERVICE_AUTHORIZATION, serviceAuthorizationNoBearer);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals("ccd_gw", securityUtils.getServiceName());
    }

    @Test
    void shouldNotGetServiceNameFromContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SERVICE_AUTHORIZATION, serviceAuthorizationNoBearer);
        RequestContextHolder.setRequestAttributes(null);
        Assertions.assertNull(securityUtils.getServiceName());
    }

}
