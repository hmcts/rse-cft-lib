package uk.gov.hmcts.rse.ccd.lib.common;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;


import com.auth0.jwt.JWT;
import java.util.Collection;
import javax.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import uk.gov.hmcts.ccd.definition.store.security.JwtGrantedAuthoritiesConverter;

@Configuration
public class CFTLibSecurityConfiguration extends WebSecurityConfigurerAdapter {

  private final JwtAuthenticationConverter jwtAuthenticationConverter;
//
  @Inject
  public CFTLibSecurityConfiguration(
      Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter
  ) {
    jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http
        .sessionManagement().sessionCreationPolicy(STATELESS).and()
        .csrf().disable()
        .formLogin().disable()
        .logout().disable()
        .authorizeRequests()
        .anyRequest()
        .permitAll() // TODO: authenticated()
        .and()
        .oauth2ResourceServer()
        .jwt()
        .jwtAuthenticationConverter(jwtAuthenticationConverter)
        .and()
        .and()
        .oauth2Client();
  }

  // Parse (but do not validate) IDAM JWTs.
  @Bean
  JwtDecoder jwtDecoder() {
    return new JwtDecoder() {
      @Override
      public Jwt decode(String token) throws JwtException {
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
    };
  }
}
