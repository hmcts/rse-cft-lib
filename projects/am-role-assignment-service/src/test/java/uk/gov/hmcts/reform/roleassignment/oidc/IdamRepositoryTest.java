package uk.gov.hmcts.reform.roleassignment.oidc;

import feign.FeignException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.UnauthorizedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class IdamRepositoryTest {

    @Mock
    private IdamApi idamApi = mock(IdamApi.class);

    @Mock
    private OIdcAdminConfiguration oidcAdminConfiguration = mock(OIdcAdminConfiguration.class);

    @Mock
    private OAuth2Configuration oauth2Configuration = mock(OAuth2Configuration.class);

    @Mock
    private CacheManager cacheManager = mock(CacheManager.class);


    private final RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

    IdamRepository idamRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idamRepository = new IdamRepository(idamApi, oidcAdminConfiguration,
                                            oauth2Configuration, restTemplate,
                                            cacheManager
        );
        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", ""

        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserInfo() {
        UserInfo userInfo = mock(UserInfo.class);
        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        when(idamApi.retrieveUserInfo(anyString())).thenReturn(userInfo);
        when(cacheManager.getCache(anyString())).thenReturn(caffeineCacheMock);
        when(caffeineCacheMock.getNativeCache()).thenReturn(cache);
        when(cache.estimatedSize()).thenReturn(anyLong());

        UserInfo returnedUserInfo = idamRepository.getUserInfo("Test");
        assertNotNull(returnedUserInfo);
        verify(idamApi, times(1)).retrieveUserInfo(any());
        verify(cacheManager, times(1)).getCache(any());
        verify(caffeineCacheMock, times(1)).getNativeCache();
        verify(cache, times(1)).estimatedSize();

    }

    @Test
    void getUserInfo_cacheNone() {
        UserInfo userInfo = mock(UserInfo.class);


        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", "none"

        );

        when(idamApi.retrieveUserInfo(anyString())).thenReturn(userInfo);

        UserInfo returnedUserInfo = idamRepository.getUserInfo("Test");
        assertNotNull(returnedUserInfo);
        verify(idamApi, times(1)).retrieveUserInfo(any());
        verify(cacheManager, times(0)).getCache(any());

        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        verify(caffeineCacheMock, times(0)).getNativeCache();
        verify(cache, times(0)).estimatedSize();

    }

    @Test
    void getUserInfo_cacheNull() {
        UserInfo userInfo = mock(UserInfo.class);


        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", null

        );

        when(idamApi.retrieveUserInfo(anyString())).thenReturn(userInfo);

        UserInfo returnedUserInfo = idamRepository.getUserInfo("Test");
        assertNotNull(returnedUserInfo);
        verify(idamApi, times(1)).retrieveUserInfo(any());
        verify(cacheManager, times(0)).getCache(any());

        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        verify(caffeineCacheMock, times(0)).getNativeCache();
        verify(cache, times(0)).estimatedSize();

    }

    @Test
    void getUserInfo_HandleHttpResponse() {
        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", "none"

        );
        when(idamApi.retrieveUserInfo(any())).thenThrow(FeignException.Unauthorized.class);

        Assertions.assertThrows(UnauthorizedException.class, () ->
            idamRepository.getUserInfo("Bearer invalid")
        );
    }

    @Test
    void getUserInfo_BadRequest() {
        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", "none"

        );
        when(idamApi.retrieveUserInfo(any())).thenThrow(FeignException.BadRequest.class);

        Assertions.assertThrows(UnauthorizedException.class, () ->
            idamRepository.getUserInfo("Bearer invalid")
        );
    }

    @Test
    void getUserRolesBlankResponse() {
        String userId = "003352d0-e699-48bc-b6f5-5810411e60af";
        UserDetails userDetails = UserDetails.builder().email("black@betty.com").forename("ram").surname("jam").id(
            "1234567890123456")
            .roles(null).build();

        when(idamApi.getUserByUserId(any(), any())).thenReturn(userDetails);

        assertNotNull(idamRepository.getUserByUserId("Test", userId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getManageUserToken() {
        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        when(cacheManager.getCache(anyString())).thenReturn(caffeineCacheMock);
        when(caffeineCacheMock.getNativeCache()).thenReturn(cache);
        when(cache.estimatedSize()).thenReturn(1L);

        when(oauth2Configuration.getClientId()).thenReturn("clientId");
        when(oauth2Configuration.getClientSecret()).thenReturn("secret");
        when(oidcAdminConfiguration.getSecret()).thenReturn("password");
        when(oidcAdminConfiguration.getScope()).thenReturn("scope");
        TokenResponse tokenResponse = new
            TokenResponse("a", "1", "1", "a", "v", "v");
        when(idamApi.generateOpenIdToken(any())).thenReturn(tokenResponse);

        String result = idamRepository.getManageUserToken("123");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertFalse(result.isEmpty());
    }

    @Test
    void getManageUserToken_cacheNone() {

        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", "none"

        );

        when(oauth2Configuration.getClientId()).thenReturn("clientId");
        when(oauth2Configuration.getClientSecret()).thenReturn("secret");
        when(oidcAdminConfiguration.getSecret()).thenReturn("password");
        when(oidcAdminConfiguration.getScope()).thenReturn("scope");
        TokenResponse tokenResponse = new
            TokenResponse("a", "1", "1", "a", "v", "v");
        when(idamApi.generateOpenIdToken(any())).thenReturn(tokenResponse);

        String result = idamRepository.getManageUserToken("123");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertFalse(result.isEmpty());
        verify(cacheManager, times(0)).getCache(any());

        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        verify(caffeineCacheMock, times(0)).getNativeCache();
        verify(cache, times(0)).estimatedSize();
    }

    @Test
    void getManageUserToken_cacheNull() {

        ReflectionTestUtils.setField(
            idamRepository,
            "cacheType", null
        );

        when(oauth2Configuration.getClientId()).thenReturn("clientId");
        when(oauth2Configuration.getClientSecret()).thenReturn("secret");
        when(oidcAdminConfiguration.getSecret()).thenReturn("password");
        when(oidcAdminConfiguration.getScope()).thenReturn("scope");
        TokenResponse tokenResponse = new
            TokenResponse("a", "1", "1", "a", "v", "v");
        when(idamApi.generateOpenIdToken(any())).thenReturn(tokenResponse);

        String result = idamRepository.getManageUserToken("123");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertFalse(result.isEmpty());
        verify(cacheManager, times(0)).getCache(any());

        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        verify(caffeineCacheMock, times(0)).getNativeCache();
        verify(cache, times(0)).estimatedSize();
    }

    @Test
    void shouldThrowNullPointerException() {

        String token = "eyJhbGciOiJIUzUxMiJ9.Eim7hdYejtBbWXnqCf1gntbYpWHRX8BRzm4zIC_oszmC3D5QlNmkIetVPcMINg";
        String userId = "4dc7dd3c-3fb5-4611-bbde-5101a97681e0";


        doThrow(NullPointerException.class)
            .when(restTemplate)
            .exchange(anyString(), any(), any(), (Class<?>) any(Class.class));

        assertThrows(NullPointerException.class, () -> idamRepository.searchUserByUserId(token, userId));


    }

    @Test
    void searchUserByUserId() {

        String token = "eyJhbGciOiJIUzUxMiJ9.Eim7hdYejtBbWXnqCf1gntbYpWHRX8BRzm4zIC_oszmC3D5QlNmkIetVPcMINg";
        String userId = "4dc7dd3c-3fb5-4611-bbde-5101a97681e0";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        ResponseEntity<List<Object>> responseEntity = new ResponseEntity<>(headers, HttpStatus.OK);

        doReturn(responseEntity)
            .when(restTemplate)
            .exchange(
                isA(String.class),
                eq(HttpMethod.GET),
                isA(HttpEntity.class),
                (ParameterizedTypeReference<?>) any(ParameterizedTypeReference.class));
        ResponseEntity<List<Object>> response = idamRepository.searchUserByUserId(token, userId);

        assertNotNull(response);
        assertNotNull(response.getHeaders());
    }

    @Test
    void shouldReturnUserRoles() {

        ResponseEntity<List<Object>> responseEntity = new ResponseEntity<>(HttpStatus.OK);
        doReturn(responseEntity)
            .when(restTemplate)
            .exchange(
                isA(String.class),
                eq(HttpMethod.GET),
                isA(HttpEntity.class),
                (ParameterizedTypeReference<?>) any(ParameterizedTypeReference.class)
            );

        String token = "eyJhbGciOiJIUzUxMiJ9.Eim7hdYejtBbWXnqCf1gntbYpWHRX8BRzm4zIC_oszmC3D5QlNmkIetVPcMINg";
        String userId = "4dc7dd3c-3fb5-4611-bbde-5101a97681e0";

        ResponseEntity<List<Object>> actualResponse = idamRepository.searchUserByUserId(token, userId);
        assertNotNull(actualResponse);
        assertEquals(HttpStatus.OK, actualResponse.getStatusCode());

    }

    @Test
    void shouldReturnUserRolesAsNull() {

        ResponseEntity<List<Object>> responseEntity = new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        doReturn(responseEntity)
            .when(restTemplate)
            .exchange(
                isA(String.class),
                eq(HttpMethod.GET),
                isA(HttpEntity.class),
                (ParameterizedTypeReference<?>) any(ParameterizedTypeReference.class)
            );

        String token = "eyJhbGciOiJIUzUxMiJ9.Eim7hdYejtBbWXnqCf1gntbYpWHRX8BRzm4zIC_oszmC3D5QlNmkIetVPcMINg";
        String userId = "4dc7dd3c-3fb5-4611-bbde-5101a97681e0";

        ResponseEntity<List<Object>> actualResponse = idamRepository.searchUserByUserId(token, userId);
        assertNull(actualResponse);

    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserInfoException() {
        UserInfo userInfo = mock(UserInfo.class);
        CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);
        var cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

        when(idamApi.retrieveUserInfo(anyString())).thenReturn(userInfo);
        when(cacheManager.getCache(anyString())).thenReturn(caffeineCacheMock);
        when(caffeineCacheMock.getNativeCache()).thenReturn(cache);
        FeignException.Unauthorized unauthorized = mock(FeignException.Unauthorized.class);
        when(idamRepository.getUserInfo("invalid token")).thenThrow(unauthorized);

        assertThrows(UnauthorizedException.class, () -> idamRepository.getUserInfo("invalid token"));
    }

    @Test
    void getUserByUserIdUnauthorizedException() {
        FeignException.Unauthorized unauthorized = mock(FeignException.Unauthorized.class);
        when(idamApi.getUserByUserId(any(), any())).thenThrow(unauthorized);

        assertThrows(
            UnauthorizedException.class,
            () -> idamRepository.getUserByUserId("token", "unauthorizedUser")
        );
    }

    @Test
    void getUserByUserIdBadRequestException() {
        FeignException.BadRequest badRequest = mock(FeignException.BadRequest.class);
        when(idamApi.getUserByUserId(any(), any())).thenThrow(badRequest);

        assertThrows(
            UnauthorizedException.class,
            () -> idamRepository.getUserByUserId("token", "@@@££££")
        );
    }

    @Test
    void getHttpHeaders() {
        assertThrows(NullPointerException.class, () -> idamRepository.searchUserByUserId(null, null));
    }
}
