package uk.gov.hmcts.reform.roleassignment;

import com.google.common.collect.Lists;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;


public class MockUtils {

    public static final String CCD_GW = "CCD_GW";
    public static final String ROLE_CASEWORKER = "caseworker";

    private MockUtils() {

    }

    public static String generateDummyS2SToken(String serviceName) {
        return Jwts.builder()
            .setSubject(serviceName)
            .setIssuedAt(new Date())
            .signWith(SignatureAlgorithm.HS256, TextCodec.BASE64.encode("AA"))
            .compact();
    }

    public static final void setSecurityAuthorities(Authentication authenticationMock, String... authorities) {
        setSecurityAuthorities("aJwtToken", authenticationMock, authorities);
    }

    public static final void setSecurityAuthorities(String jwtToken,
                                                    Authentication authenticationMock,
                                                    String... authorities) {

        Jwt jwt = Jwt.withTokenValue(jwtToken)
            .claim("aClaim", "aClaim")
            .claim("aud", Lists.newArrayList(CCD_GW,"am_role_assignment_service"))
            .header("aHeader", "aHeader")
            .build();
        when(authenticationMock.getPrincipal()).thenReturn(jwt);

        Collection<? extends GrantedAuthority> authorityCollection = Stream.of(authorities)
            .map(a -> new SimpleGrantedAuthority(a))
            .collect(Collectors.toCollection(ArrayList::new));

        when(authenticationMock.getAuthorities()).thenAnswer(invocationOnMock -> authorityCollection);

    }
}
