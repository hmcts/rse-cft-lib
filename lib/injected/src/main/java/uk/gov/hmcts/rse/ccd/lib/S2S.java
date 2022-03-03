package uk.gov.hmcts.rse.ccd.lib;


import java.util.Date;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.auth.parser.idam.core.service.token.ServiceTokenParser;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;

@Configuration
@ConditionalOnClass(ServiceTokenParser.class)
@Aspect
class NonValidatingS2SParser {


    // Intercept S2S validation requests and parse the token locally without validating the signature.
    // This allows self-signed s2s tokens and breaks the dependency on s2s.
    @Around("execution(* uk.gov.hmcts.reform.auth.parser.idam.core.service.token.ServiceTokenParser.parse(..)) && args(jwt)")
    public Object parse(ProceedingJoinPoint p, String jwt) throws Throwable {
        return JWT.decode(jwt.replace("Bearer ", "")).getSubject();
    }

}
@Component
@ConditionalOnClass(ServiceAuthorisationApi.class)
@Aspect
class S2SLib {

    private final String service;

    @Autowired
    public S2SLib(@Value("${idam.s2s-auth.microservice}") String service) {
        this.service = service;
    }

    @Around("execution(* uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi.getServiceName(..)) && args(authHeader)")
    public Object getServiceName(ProceedingJoinPoint p, String authHeader) {
        var subject = JWT.decode(authHeader.replace("Bearer ", "")).getSubject();

        // Allow XUI to talk direct to CCD by making xui appear to be the ccd gateway to ccd data store.
        if (this.service.equals("ccd_data") && subject.equals("xui_webapp")) {
            return "ccd_gw";
        }
        return subject;
    }

    @Around("execution(* uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi.serviceToken(..)) && args(signIn)")
    public Object serviceToken(ProceedingJoinPoint p, Map<String, String> signIn) {
       var j = JWT.create()
            .withSubject(signIn.get("microservice"))
            .withIssuedAt(new Date())
            .sign(Algorithm.HMAC256("secret"));

        return j;
    }
}
