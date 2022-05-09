package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.util.DigestUtils;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

import static java.lang.System.getenv;

public class CFTLibApiImpl implements CFTLib {

  private String lastImportHash;

  @SneakyThrows
  @Override
  public void createIdamUser(String email, String... roles) {
    // TODO: Allow creation of augmented accounts.
    if (!"localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
      return;
    }
    var json = new Gson().toJson(Map.of(
      "email", email,
         "forename", "A",
      "surname", "User",
      "password", "password",
      "roles", Arrays.stream(roles).map(x -> Map.of("code", x)).collect(Collectors.toList())
      ));
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:5000/testing-support/accounts"))
      .header("content-type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build();

    var client = HttpClient.newHttpClient();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (!String.valueOf(response.statusCode()).startsWith("2")) {
      throw new RuntimeException("Failed to create idam account" + response.statusCode());
    }
  }

  @SneakyThrows
  public void createProfile(String id, String jurisdiction, String caseType, String state) {
      var json = new Gson().toJson(List.of(Map.of(
          "id", id,
          "work_basket_default_jurisdiction", jurisdiction,
          "work_basket_default_case_type", caseType,
          "work_basket_default_state", state
      )));
      var request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:4453/user-profile/users"))
          .header("content-type", "application/json")
          .header("ServiceAuthorization", generateDummyS2SToken("ccd_data"))
              .PUT(HttpRequest.BodyPublishers.ofString(json))
          .build();

      var client = HttpClient.newHttpClient();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (!String.valueOf(response.statusCode()).startsWith("2")) {
          throw new RuntimeException("Failed to create user profile: HTTP " + response.statusCode());
      }
  }

    public static String generateDummyS2SToken(String serviceName) {
        return JWT.create()
            .withSubject(serviceName)
            .withIssuedAt(new Date())
            .sign(Algorithm.HMAC256("secret"));
  }


  @SneakyThrows
  public void createRoles(String... roles) {
    for (String role : roles) {
        var json = new Gson().toJson(Map.of(
            "role", role,
            "security_classification", "PUBLIC"
        ));

        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:4451/api/user-role"))
            .header("content-type", "application/json")
            .header("Authorization", "Bearer " + buildJwt())
            .header("ServiceAuthorization", generateDummyS2SToken("ccd_data"))
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();

        var client = HttpClient.newHttpClient();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (!String.valueOf(response.statusCode()).startsWith("2")) {
            throw new RuntimeException("Failed to create role: HTTP " + response.statusCode() + " " + response.body());
        }
    }
  }

  @SneakyThrows
  public void configureRoleAssignments(String json){
      var port = getenv("CFT_LIB_DB_HOST") != null ? 5432 : 6432;
      var host = getenv("CFT_LIB_DB_HOST") != null ? getenv("CFT_LIB_DB_HOST") : "localhost";
      try (var c = DriverManager.getConnection(
          "jdbc:postgresql://" + host + ":" + port + "/am",
          "postgres", "postgres")) {
          // To use the uuid generation function.
          c.createStatement().execute(
              "create extension if not exists pgcrypto"
          );

          var url = CFTLibApiImpl.class.getClassLoader().getResource("cftlib-populate-am.sql");
          var sql = Resources.toString(url, StandardCharsets.UTF_8);
          var p = c.prepareStatement(sql);
          p.setString(1, json);
          p.executeQuery();
      }
  }

  @SneakyThrows
  public void importDefinition(byte[] def) {
      // Track the last imported definition and skip the import if there is no change.
      var hash = DigestUtils.md5DigestAsHex(def);
      if (lastImportHash != null) {
          if (hash.equals(lastImportHash)) {
              System.out.println("Definition up to date, no import necessary!");
              return;
          }
      }
      lastImportHash = hash;
      CloseableHttpClient httpClient = HttpClients.createDefault();
      HttpPost uploadFile = new HttpPost("http://localhost:8489/import");
      uploadFile.addHeader("Authorization", "Bearer " + buildJwt());
      uploadFile.addHeader("ServiceAuthorization", generateDummyS2SToken("ccd_gw"));
      MultipartEntityBuilder builder = MultipartEntityBuilder.create();
      builder.addBinaryBody(
          "file",
          def,
          ContentType.MULTIPART_FORM_DATA,
          "definition"
      );

      HttpEntity multipart = builder.build();
      uploadFile.setEntity(multipart);
      CloseableHttpResponse response = httpClient.execute(uploadFile);

      if (!String.valueOf(response.getStatusLine().getStatusCode()).startsWith("2")) {
          var body = EntityUtils.toString(response.getEntity());
          throw new RuntimeException("Failed to import definition: HTTP " + response.getStatusLine().getStatusCode() + " " + body);
      }
  }

  @SneakyThrows
  @Override
  public void importDefinition(File def) {
    importDefinition(Files.readAllBytes(def.toPath()));
  }

  public static String buildJwt() {
    return JWT.create()
        .withSubject("banderous")
        .withNotBefore(new Date())
        .withIssuedAt(new Date())
        .withExpiresAt(Date.from(LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)))
        .withClaim("tokenName", "access_token")
        .sign(Algorithm.HMAC256("secret"));
  }
}
