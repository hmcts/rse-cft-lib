package uk.gov.hmcts.rse.ccd.lib.api;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
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
}
