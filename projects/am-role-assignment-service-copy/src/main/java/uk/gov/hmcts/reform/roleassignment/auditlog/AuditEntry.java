package uk.gov.hmcts.reform.roleassignment.auditlog;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class AuditEntry {

    private String dateTime;
    private int httpStatus;
    private String httpMethod;
    private String authenticatedUserId;
    private String assignerId;
    private String roleName;
    private String assignmentId;
    private String actorId;
    private String process;
    private String invokingService;
    private String operationType;
    private String reference;
    private String path;
    private String correlationId;
    private String requestPayload;
    private Integer assignmentSize;
    private Long responseTime;


}
