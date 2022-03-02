package uk.gov.hmcts.rse.ccd.lib;


import com.auth0.jwt.JWT;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

@Configuration
@ConditionalOnClass(UserInfo.class)
@Aspect
class IdamAugmenter {

  @Around("execution(* uk.gov.hmcts.reform.idam.client.IdamApi.retrieveUserInfo(..)) && args(authorisation)")
  public Object retrieveUserInfo(ProceedingJoinPoint p, String authorisation) throws Throwable {
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
    return p.proceed();
  }

}
