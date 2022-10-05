package uk.gov.hmcts.rse;

public enum Project {
    AMRoleAssignmentService("am-role-assignment-service", "uk.gov.hmcts.reform.roleassignment.RoleAssignmentApplication"),
    CCDDataStore("ccd-data-store-api", "uk.gov.hmcts.ccd.CoreCaseDataApplication"),
    CCDDefinitionStore("application", "uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication", "ccd-definition-store-api"),
    CCDUserProfile("user-profile-api", "uk.gov.hmcts.ccd.UserProfileApplication"),
    AACManageCaseAssignment("aac-manage-case-assignment", "uk.gov.hmcts.reform.managecase.Application"),
    CCDCaseDocumentAM("ccd-case-document-am-api", "uk.gov.hmcts.reform.ccd.documentam.Application"),
    CftlibAgent("cftlib-agent", null),
    TestRunner("test-runner", null),
    Runtime("runtime", "uk.gov.hmcts.rse.ccd.lib.Application"),
    Bootstrapper("bootstrapper", "uk.gov.hmcts.rse.ccd.lib.LibRunner");

    Project(String name, String mainClass) {
        this.name = name;
        this.group = "com.github.hmcts.rse-cft-lib." + name;
        this.mainClass = mainClass;
    }

    Project(String name, String mainClass, String group) {
        this.name = name;
        this.group = "com.github.hmcts.rse-cft-lib." + group;
        this.mainClass = mainClass;
    }

    public String mavenCoordinates(String version) {
        return group + ":" + name + ":" + version;
    }
    public final String name, group;
    public final String mainClass;
}
