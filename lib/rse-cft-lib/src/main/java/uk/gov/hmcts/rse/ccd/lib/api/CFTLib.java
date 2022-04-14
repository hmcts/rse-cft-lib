package uk.gov.hmcts.rse.ccd.lib.api;

import java.io.File;

public interface CFTLib {
  /**
   * Create an IDAM user.
   * Password 'password'.
   */
  void createIdamUser(String email, String... roles);

  /**
   * Create a CCD User Profile
   */
  void createProfile(String id, String jurisdiction, String caseType, String state);

  /**
   * Create roles in CCD Definition store
   */
  void createRoles(String... roles);

  /**
   * Configure the AM role assignment service
   * For format see https://github.com/hmcts/rse-cft-lib/blob/main/lib-consumer/src/cftlib/resources/cftlib-am-role-assignments.json
   */
  void configureRoleAssignments(String json);

  /**
   * Import a CCD definition spreadsheet.
   * @param def A Microsoft xlsx file
   */
  void importDefinition(byte[] def);

  /**
   * Import a CCD definition spreadsheet.
   * @param def A Microsoft xlsx file
   */
  void importDefinition(File def);
}
