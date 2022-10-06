package uk.gov.hmcts.rse.ccd.lib;

import java.util.Date;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;

@Component
@ConditionalOnClass(ServiceAuthorisationApi.class)
@Aspect
class S2SLib {

    private final String service;
    private final boolean stubOutbound;

    @Autowired
    public S2SLib(@Value("${idam.s2s-auth.microservice}") String service,
                  @Value("${rse.lib.stub.auth.outbound:false}") boolean stubOutbound
    ) {
        this.service = service;
        this.stubOutbound = stubOutbound;
    }

    @SneakyThrows
    @Around("execution(* uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi.getServiceName(..))"
        + " && args(authHeader)")
    public Object getServiceName(ProceedingJoinPoint p, String authHeader) {
        if (null != authHeader) {
            try {
                var subject = JWT.decode(authHeader.replace("Bearer ", "")).getSubject();

                // Allow XUI to talk direct to CCD by making xui appear to be the ccd gateway to ccd data store.
                if (this.service.equals("ccd_data") && subject.equals("xui_webapp")) {
                    return "ccd_gw";
                }
                return subject;
            } catch (JWTDecodeException j) {
                // Let invalid JWTs proceed to be handled as normal, ie. unauthorised.
            }
        }
        return p.proceed();
    }

    @SneakyThrows
    @Around("execution(* uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi.serviceToken(..)) && args(signIn)")
    public Object serviceToken(ProceedingJoinPoint p, Map<String, String> signIn) {
        if (stubOutbound) {
            // Return a self-signed JWT.
            return JWT.create()
                .withSubject(signIn.get("microservice"))
                .withIssuedAt(new Date())
                .sign(Algorithm.HMAC256("secret"));
        }

        return p.proceed();
    }
}
