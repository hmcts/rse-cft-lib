package uk.gov.hmcts.reform.roleassignment.domain.model;

public class CaseAllocatorApproval {
    private RoleAssignment roleAssignment;

    public CaseAllocatorApproval(RoleAssignment roleAssignment) {
        this.roleAssignment = roleAssignment;
    }

    public RoleAssignment getRoleAssignment() {
        return roleAssignment;
    }
}
