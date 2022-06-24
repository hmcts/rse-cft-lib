package uk.gov.hmcts.rse.ccd.lib;


import com.auth0.jwt.JWT;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.auth.parser.idam.core.service.token.ServiceTokenParser;

@Configuration
@ConditionalOnClass(ServiceTokenParser.class)
@Aspect
class NonValidatingS2SParser {

    // Intercept S2S validation requests and parse the token locally without validating the signature.
    // This allows self-signed s2s tokens and breaks the dependency on s2s.
    @Around("execution(* uk.gov.hmcts.reform.auth.parser.idam.core.service.token.ServiceTokenParser.parse(..))"
        + " && args(jwt)")
    public Object parse(ProceedingJoinPoint p, String jwt) throws Throwable {
        return JWT.decode(jwt.replace("Bearer ", "")).getSubject();
    }
}

