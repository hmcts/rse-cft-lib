package uk.gov.hmcts.reform.roleassignment.auditlog;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.auditlog.aop.AuditContext;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import java.time.Clock;
import java.time.LocalDateTime;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@Service
public class AuditService {

    private final Clock clock;
    private final SecurityUtils securityUtils;
    private final AuditRepository auditRepository;

    public AuditService(@Qualifier("utcClock") final Clock clock,
                        @Lazy final SecurityUtils securityUtils, final AuditRepository auditRepository) {
        this.clock = clock;
        this.securityUtils = securityUtils;
        this.auditRepository = auditRepository;
    }

    public void audit(AuditContext auditContext) {

        var entry = new AuditEntry();
        var formattedDate = LocalDateTime.now(clock).format(ISO_LOCAL_DATE_TIME);
        entry.setDateTime(formattedDate);
        entry.setActorId(auditContext.getActorId());
        entry.setHttpStatus(auditContext.getHttpStatus());
        entry.setHttpMethod(auditContext.getHttpMethod());
        entry.setProcess(auditContext.getProcess());
        entry.setReference(auditContext.getReference());
        entry.setInvokingService(securityUtils.getServiceName());
        entry.setOperationType(auditContext.getAuditOperationType() != null
                                   ? auditContext.getAuditOperationType().getLabel() : null);
        entry.setAssignerId(auditContext.getAssignerId());
        entry.setAssignmentId(auditContext.getAssignmentId());
        entry.setRoleName(auditContext.getRoleName());
        entry.setPath(auditContext.getRequestPath());
        entry.setAuthenticatedUserId(securityUtils.getUserId());
        entry.setCorrelationId(auditContext.getCorrelationId());
        entry.setRequestPayload(auditContext.getRequestPayload());
        entry.setAssignmentSize(auditContext.getAssignmentSize());
        entry.setResponseTime(auditContext.getResponseTime());
        auditRepository.save(entry);

    }

}
