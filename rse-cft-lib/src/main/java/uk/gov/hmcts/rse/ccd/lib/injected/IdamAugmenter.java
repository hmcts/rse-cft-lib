package uk.gov.hmcts.rse.ccd.lib.injected;


import com.auth0.jwt.JWT;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.rse.ccd.lib.impl.ComposeRunner;

@Configuration
@Aspect
class IdamAugmenter {

  @Around("execution(* uk.gov.hmcts.reform.idam.client.IdamApi.retrieveUserInfo(..)) && args(authorisation)")
  public UserInfo retrieveUserInfo(ProceedingJoinPoint p, String authorisation) throws Throwable {
    var j = JWT.decode(authorisation.replace("Bearer ", ""));
    if (j.getSubject().equals("banderous")) {
      return UserInfo.builder()
          .givenName("A")
          .familyName("Dev")
          .uid("banderous")
          .sub("a@b.com")
          .roles(List.of("caseworker-divorce-solicitor"))
          .build();
    }
    return (UserInfo) p.proceed();
  }

  @Before("execution(* uk.gov.hmcts.ccd.definition.store.elastic.client.*.*(..))")
  public void checkES() {
    ComposeRunner.waitForES();
  }
}
