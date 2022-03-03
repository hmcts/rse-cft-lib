package uk.gov.hmcts.rse.ccd.lib.api;

public interface CFTLib {
  void createProfile(String id, String jurisdiction, String caseType, String state);
  void createRoles(String... roles);
  void configureRoleAssignments(String json);

  void importDefinition(byte[] def);
}
