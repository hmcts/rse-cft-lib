package uk.gov.hmcts.rse.ccd.lib;


import java.util.List;

import com.auth0.jwt.JWT;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
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
