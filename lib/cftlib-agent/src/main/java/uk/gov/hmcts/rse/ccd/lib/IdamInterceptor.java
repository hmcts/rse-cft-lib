package uk.gov.hmcts.rse.ccd.lib;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;
import uk.gov.hmcts.reform.idam.client.models.TokenRequest;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

@Component
@ConditionalOnClass({
    UserInfo.class,
    JWT.class
})
@ConditionalOnProperty("rse.lib.stub.auth.outbound")
@Aspect
class IdamInterceptor {

    @Autowired
    OAuth2Configuration config;

    @Around("execution(* uk.gov.hmcts.reform.idam.client.IdamApi.generateOpenIdToken(..)) && args(tokenRequest)")
    public Object generateOpenIdToken(ProceedingJoinPoint p, TokenRequest tokenRequest) throws Throwable {

        var token = JWT.create()
            .withSubject(tokenRequest.getUsername())
            .withNotBefore(new Date())
            .withIssuedAt(new Date())
            .withIssuer("rse-fake-idam")
            .withExpiresAt(Date.from(LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)))
            .withClaim("tokenName", "access_token")
            .withClaim("aud", config.getClientId())
            .withClaim("grant_type", "password")
            .withClaim("scope", config.getClientScope())
            .sign(Algorithm.HMAC256("secret"));

        return new TokenResponse(token,
            "30000",
            token,
            token,
            config.getClientScope(),
            "Bearer"
        );
    }
}
