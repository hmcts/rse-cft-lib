package uk.gov.hmcts.rse.ccd.lib.test;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.ControlPlane;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

import java.util.Date;

/**
 * Base class for Junit tests that test against the cftlib.
 */
public class CftlibTest {
    private static CFTLib cftlib;

    @SneakyThrows
    @BeforeAll
    void setup() {
        ControlPlane.waitForBoot();
    }

    /**
     * Access the Cftlib API.
     * @see CFTLib
     */
    protected CFTLib cftlib() {
        return cftlib;
    }

    @Component
    static class CftlibListener implements CFTLibConfigurer {

        @Override
        public void configure(CFTLib lib) throws Exception {
            cftlib = lib;
        }
    }

    protected String generateDummyS2SToken(String serviceName) {
        return JWT.create()
                .withSubject(serviceName)
                .withIssuedAt(new Date())
                .sign(Algorithm.HMAC256("secret"));
    }
}
