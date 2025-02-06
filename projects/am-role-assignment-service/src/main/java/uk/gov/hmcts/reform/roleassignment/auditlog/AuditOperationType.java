package uk.gov.hmcts.reform.roleassignment.auditlog;

public enum AuditOperationType {
    CREATE_ASSIGNMENTS("Create assignments"),
    DELETE_ASSIGNMENTS_BY_PROCESS("Delete assignments by Process"),
    DELETE_ASSIGNMENTS_BY_ID("Delete assignment by Id"),
    DELETE_ASSIGNMENTS_BY_QUERY("Delete assignments by Query"),
    GET_ASSIGNMENTS_BY_ACTOR("Get assignments by Actor"),
    SEARCH_ASSIGNMENTS("Search assignments");

    private final String label;

    AuditOperationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
