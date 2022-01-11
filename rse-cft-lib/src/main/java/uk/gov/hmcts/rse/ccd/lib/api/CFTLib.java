package uk.gov.hmcts.rse.ccd.lib.api;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.UserRole;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.ccd.domain.model.UserProfile;
import uk.gov.hmcts.ccd.endpoint.userprofile.UserProfileEndpoint;

@Component
public class CFTLib {
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
}
