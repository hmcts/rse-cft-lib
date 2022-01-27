package uk.gov.hmcts.rse.ccd.lib.common;


import com.auth0.jwt.JWT;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

@Configuration
@Aspect
class IdamConfig {

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
}
