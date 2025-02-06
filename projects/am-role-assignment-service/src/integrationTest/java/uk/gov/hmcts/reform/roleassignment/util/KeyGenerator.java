package uk.gov.hmcts.reform.roleassignment.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

public final class KeyGenerator {

    private static RSAKey rsaJwk;
    private static final String KEY_ID = "23456789";

    private KeyGenerator() {
    }

    public static RSAKey getRsaJwk() throws JOSEException {
        if (rsaJwk == null) {
            rsaJwk = new RSAKeyGenerator(2048)
                .keyID(KEY_ID)
                .generate();
        }
        return rsaJwk;
    }

}
