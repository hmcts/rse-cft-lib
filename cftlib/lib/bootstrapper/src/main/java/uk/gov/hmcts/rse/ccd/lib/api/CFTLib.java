package uk.gov.hmcts.rse.ccd.lib.api;

import uk.gov.hmcts.rse.ccd.lib.Database;

import java.io.File;
import java.sql.Connection;

public interface CFTLib {
    /**
     * Create an IDAM user.
     * Password 'password'.
     */
    void createIdamUser(String email, String... roles);

    /**
     * Create a CCD User Profile.
     */
    void createProfile(String id, String jurisdiction, String caseType, String state);

    /**
     * Create roles in CCD Definition store.
     */
    void createRoles(String... roles);

    /**
     * Configure the AM role assignment service
     * For format see https://github.com/hmcts/rse-cft-lib/blob/main/test-project/src/cftlib/resources/cftlib-am-role-assignments.json
     */
    void configureRoleAssignments(String json);

    /**
     * Import a CCD definition spreadsheet.
     *
     * @param def A Microsoft xlsx file
     */
    void importDefinition(byte[] def);

    /**
     * Import a CCD definition spreadsheet.
     *
     * @param def A Microsoft xlsx file
     */
    void importDefinition(File def);

    /**
     * Import a CCD definition from json in the format used by the ccd definition processor.
     *
     * @param defFolder folder containing json ccd definition
     */
    void importJsonDefinition(File defFolder);

    /**
     * Obtain connections to common component databases.
     */
    Connection getConnection(Database database);

    /**
     * Private method invoked automatically to create CCD's global search elasticsearch index.
     */
    void createGlobalSearchIndex();

    String generateDummyS2SToken(String serviceName);

    String buildJwt();
}
