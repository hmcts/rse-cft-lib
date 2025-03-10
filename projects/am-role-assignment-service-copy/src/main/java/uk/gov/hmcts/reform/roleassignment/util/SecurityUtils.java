package uk.gov.hmcts.reform.roleassignment.util;

import com.auth0.jwt.JWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.oidc.IdamRepository;

import java.util.Collection;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.roleassignment.util.Constants.BEARER;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION;

@Service
@Slf4j
public class SecurityUtils {

    private final AuthTokenGenerator authTokenGenerator;
    private final IdamRepository idamRepository;

    @Autowired
    public SecurityUtils(final AuthTokenGenerator authTokenGenerator,
                         IdamRepository idamRepository
                         ) {
        this.authTokenGenerator = authTokenGenerator;
        this.idamRepository = idamRepository;

    }

    public HttpHeaders authorizationHeaders() {
        final var headers = new HttpHeaders();
        headers.add(SERVICE_AUTHORIZATION, authTokenGenerator.generate());
        headers.add("user-id", getUserId());
        headers.add("user-roles", getUserRolesHeader());

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            headers.add(HttpHeaders.AUTHORIZATION, getUserBearerToken());
        }
        return headers;
    }

    String getUserBearerToken() {
        return BEARER + getUserToken();
    }

    public UserInfo getUserInfo() {
        UserInfo userInfo = idamRepository.getUserInfo(getUserToken());
        if (userInfo != null) {
            log.info("SecurityUtils retrieved user info from idamRepository. User Id={}. Roles={}.",
                     userInfo.getUid(),
                     userInfo.getRoles());
        }
        return userInfo;
    }

    public String getUserId() {
        return getUserInfo().getUid();
    }

    public String getUserToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getTokenValue();
    }

    public String getUserRolesHeader() {
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities();
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));
    }


    public String getServiceName() {
        var servletRequestAttributes =
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());

        if (servletRequestAttributes != null
            && servletRequestAttributes.getRequest().getHeader(SERVICE_AUTHORIZATION) != null) {
            return JWT.decode(removeBearerFromToken(servletRequestAttributes.getRequest().getHeader(
                SERVICE_AUTHORIZATION))).getSubject();
        }
        return null;
    }

    private String removeBearerFromToken(String token) {
        if (!token.startsWith(BEARER)) {
            return token;
        } else {
            return token.substring(BEARER.length());
        }
    }

    public String getServiceAuthorizationHeader() {
        return authTokenGenerator.generate();
    }

}
