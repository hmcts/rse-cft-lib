package uk.gov.hmcts.reform.roleassignment.auditlog;

public interface AuditRepository {

    void save(AuditEntry auditEntry);
}
