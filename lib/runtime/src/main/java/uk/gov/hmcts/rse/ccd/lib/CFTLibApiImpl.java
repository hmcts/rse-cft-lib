package uk.gov.hmcts.rse.ccd.lib;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

public class CFTLibApiImpl implements CFTLib {
//
//  UserRoleController roleController;
//  UserProfileEndpoint userProfile;
//
//  @Autowired
//  CFTLibConfigurer configurer;
//
//  private DataSource amDB;
//
//  @SneakyThrows
//  public void init(UserRoleController role, UserProfileEndpoint profile, DataSource amDB) {
//    this.roleController = role;
//    this.userProfile = profile;
//    this.amDB = amDB;
//    configurer.configure(this);
//  }
//

  @SneakyThrows
  public void createProfile(String id, String jurisdiction, String caseType, String state) {
      var json = new Gson().toJson(Map.of(
          "id", id,
          "work_basket_default_jurisdiction", jurisdiction,
          "work_basket_default_case_type", caseType,
          "work_basket_default_state", state
      ));
      var request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:4453/user-profile/users"))
          .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();

      var client = HttpClient.newHttpClient();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (!String.valueOf(response.statusCode()).startsWith("2")) {
          throw new RuntimeException("Failed to create user profile");
      }
//    var p = new UserProfile();
//    p.setId(id);
//    p.setId(id);
//    p.setWorkBasketDefaultJurisdiction(jurisdiction);
//    p.setWorkBasketDefaultCaseType(caseType);
//    p.setWorkBasketDefaultState(state);
//    userProfile.populateUserProfiles(List.of(p), "banderous");
//
//          HttpHeaders headers = new HttpHeaders();
//    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//    headers.setBearerAuth(buildJwt());
//
//    HttpEntity<MultiValueMap<String, Object>> requestEntity
//        = new HttpEntity<>(body, headers);
//
//    RestTemplate restTemplate = new RestTemplate();
//    ResponseEntity<String> response = restTemplate
//        .postForEntity("http://localhost:4451/import", requestEntity, String.class);
//
  }
//
//  public void createRoles(String... roles) {
//    for (String role : roles) {
//      UserRole r = new UserRole();
//      r.setRole(role);
//      r.setSecurityClassification(SecurityClassification.PUBLIC);
//      roleController.userRolePut(r);
//    }
//  }
//
//  @SneakyThrows
//  public void configureRoleAssignments(String json){
//    try (Connection c = amDB.getConnection()) {
//      // To use the uuid generation function.
//      c.createStatement().execute(
//          "create extension if not exists pgcrypto"
//      );
//
//      ResourceLoader resourceLoader = new DefaultResourceLoader();
//      var sql = IOUtils.toString(resourceLoader.getResource("classpath:rse/cftlib-populate-am.sql").getInputStream(), Charset.defaultCharset());
//      var p = c.prepareStatement(sql);
//      p.setString(1, json);
//      p.executeQuery();
//    }
//  }
//
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
