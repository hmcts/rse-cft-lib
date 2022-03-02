package uk.gov.hmcts.rse.ccd.lib.api;

//import com.auth0.jwt.JWT;
//import com.auth0.jwt.algorithms.Algorithm;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.SignatureAlgorithm;
//import io.jsonwebtoken.impl.TextCodec;
//import java.nio.charset.Charset;
//import java.sql.Connection;
//import java.time.LocalDateTime;
//import java.time.ZoneOffset;
//import java.util.Date;
//import java.util.List;
//import javax.sql.DataSource;
//import lombok.SneakyThrows;
//import org.apache.commons.io.IOUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.core.io.DefaultResourceLoader;
//import org.springframework.core.io.ResourceLoader;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Component;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
//import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
//import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
//import uk.gov.hmcts.ccd.userprofile.domain.model.UserProfile;
//import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;

public interface CFTLib {
  void createProfile(String id, String jurisdiction, String caseType, String state);
  void createRoles(String... roles);
  void configureRoleAssignments(String json);

//  public void importDefinition(byte[] def) {
//    MultiValueMap<String, Object> body
//        = new LinkedMultiValueMap<>();
//    body.add("file", new ByteArrayResource(def) {
//      @Override
//      public String getFilename() {
//        return "definition";
//      }
//    });
//
//    HttpHeaders headers = new HttpHeaders();
//    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//    headers.setBearerAuth(buildJwt());
//
//    HttpEntity<MultiValueMap<String, Object>> requestEntity
//        = new HttpEntity<>(body, headers);
//
//    RestTemplate restTemplate = new RestTemplate();
//    ResponseEntity<String> response = restTemplate
//        .postForEntity("http://localhost:4451/import", requestEntity, String.class);
//  }
//
//  public static String buildJwt() {
//    return JWT.create()
//        .withSubject("banderous")
//        .withNotBefore(new Date())
//        .withIssuedAt(new Date())
//        .withClaim("tokenName", "access_token")
//        .withExpiresAt(Date.from(LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)))
//        .sign(Algorithm.HMAC256("a secret"));
//  }
//
//  public static String generateDummyS2SToken(String serviceName) {
//    return Jwts.builder()
//        .setSubject(serviceName)
//        .setIssuedAt(new Date())
//        .signWith(SignatureAlgorithm.HS256, TextCodec.BASE64.encode("AA"))
//        .compact();
//
//  }
}
