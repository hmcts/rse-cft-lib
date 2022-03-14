package uk.gov.hmcts.rse.ccd.lib;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;
import uk.gov.hmcts.reform.idam.client.models.TokenRequest;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

@Component
@ConditionalOnClass(UserInfo.class)
@Aspect
class IdamAugmenter implements BeanPostProcessor, MethodInterceptor {

  @Around("execution(* uk.gov.hmcts.reform.idam.client.IdamApi.retrieveUserInfo(..)) && args(authorisation)")
  public Object retrieveUserInfo(ProceedingJoinPoint p, String authorisation) throws Throwable {
    var j = JWT.decode(authorisation.replace("Bearer ", ""));
    if (j.getSubject().equals("banderous")) {
      return UserInfo.builder()
          .givenName("A")
          .familyName("Dev")
          .uid("12345")
          .sub("banderous")
          .roles(List.of("ccd-import", "caseworker", "caseworker-divorce-solicitor"))
          .build();
    }
    return p.proceed();
  }

    @Around("execution(* uk.gov.hmcts.reform.idam.client.IdamApi.getUserByUserId(..)) && args(authorisation, userId)")
    public Object getUserByUserId(ProceedingJoinPoint p, String authorisation, String userId) throws Throwable {
        if (userId.equals("12345")) {
            return UserDetails.builder()
                .forename("A")
                .surname("Dev")
                .id("12345")
                .email("banderous")
                .roles(List.of("ccd-import", "caseworker", "caseworker-divorce-solicitor"))
                .build();
        }
        return p.proceed();
    }

    /**
     *  Intercept JwtDecoder interface to parse JWTs locally without posting them to idam for validation,
     *  thus removing the idam dependency for testing.
     *  AspectJ cannot be used for this since the NimbusJwtDecoder the applications use is final
     *  and thus cannot be subclassed by the default cglib proxying mechanism.
     */
    public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
        if (bean != null) {
            if (bean instanceof JwtDecoder) {
                ProxyFactory fac = new ProxyFactory(bean);
                fac.setProxyTargetClass(false);
                fac.addAdvice(this);
                return fac.getProxy();
            }
        }
        return bean;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      if (invocation.getMethod().getName().equals("decode")) {
          var token = (String) invocation.getArguments()[0];
          var j = JWT.decode(token);
          var r = Jwt.withTokenValue(token)
              .header("typ", "JWT")
              .header("alg", "HS256")
              .claim("tokenName", j.getClaim("tokenName").asString())
              .issuer(j.getIssuer())
              .issuedAt(j.getIssuedAt().toInstant())
              .notBefore(j.getNotBefore().toInstant())
              .expiresAt(j.getExpiresAt().toInstant())
              .subject(j.getSubject())
              .audience(j.getAudience())
              .build();
          return r;
      }
      return invocation.proceed();
    }
}

@Component
@ConditionalOnClass(UserInfo.class)
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

