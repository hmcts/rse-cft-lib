package uk.gov.hmcts.rse.ccd.lib.api;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.File;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.ImportController;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.ccd.domain.model.UserProfile;
import uk.gov.hmcts.ccd.endpoint.userprofile.UserProfileEndpoint;

@Component
public class CFTLib {
  @Autowired
  private DataSource data;

  @Autowired
  UserRoleController roleController;

  @Autowired
  UserProfileEndpoint userProfile;

  @Autowired
  ImportController importController;

  @Autowired
  CFTLibConfigurer configurer;

  @Value("http://localhost:${server.port}")
  private String baseUrl;

  @EventListener(ApplicationReadyEvent.class)
  public void configure() {
    configurer.configure(this);
  }

  public void createProfile(String id) {
    var p = new UserProfile();
    p.setId(id);
    p.setId(id);
    p.setWorkBasketDefaultJurisdiction("DIVORCE");
    p.setWorkBasketDefaultCaseType("NO_FAULT_DIVORCE");
    p.setWorkBasketDefaultState("Submitted");
    userProfile.populateUserProfiles(List.of(p), "banderous");
  }

  public void createRoles(String... roles) {
    for (String role : roles) {
      UserRole r = new UserRole();
      r.setRole(role);
      r.setSecurityClassification(SecurityClassification.PUBLIC);
      roleController.userRolePut(r);
    }
  }

  @SneakyThrows
  public void configureRoleAssignments(String json){
    try (Connection c = data.getConnection()) {
      // To use the uuid generation function.
      c.createStatement().execute(
          "create extension pgcrypto"
      );

      ResourceLoader resourceLoader = new DefaultResourceLoader();
      // Provided by the consuming application.
      var sql = IOUtils.toString(resourceLoader.getResource("classpath:rse/cftlib-populate-am.sql").getInputStream(), Charset.defaultCharset());
      var p = c.prepareStatement(sql);
      p.setString(1, json);
      p.executeQuery();
    }
  }

  public void importDefinition(byte[] def) {
    MultiValueMap<String, Object> body
        = new LinkedMultiValueMap<>();
    body.add("file", new ByteArrayResource(def) {
      @Override
      public String getFilename() {
        return "definition";
      }
    });

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.setBearerAuth(buildJwt());

    HttpEntity<MultiValueMap<String, Object>> requestEntity
        = new HttpEntity<>(body, headers);

    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> response = restTemplate
        .postForEntity(baseUrl + "/import", requestEntity, String.class);
  }

  public static String buildJwt() {
    return JWT.create()
        .withSubject("banderous")
        .withNotBefore(new Date())
        .withIssuedAt(new Date())
        .withClaim("tokenName", "access_token")
        .withExpiresAt(Date.from(LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)))
        .sign(Algorithm.HMAC256("a secret"));
  }
}
