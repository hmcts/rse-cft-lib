package uk.gov.hmcts.reform.roleassignment.auditlog.aop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType;

import java.util.List;

@Builder(builderMethodName = "auditContextWith")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditContext {

    public static final int MAX_CASE_IDS_LIST = 10;
    public static final String CASE_ID_SEPARATOR = ",";

    private String caseType;
    private String caseTypeIds;
    private String jurisdiction;
    private String eventName;
    private String targetIdamId;
    private List<String> targetCaseRoles;
    private int httpStatus;
    private String httpMethod;
    private String requestPath;
    private String requestId;
    private String roleName;
    private String assignerId;
    private String assignmentId;
    private String actorId;
    private String process;
    private String reference;
    private AuditOperationType auditOperationType;
    private String correlationId;
    private String requestPayload;
    private Integer assignmentSize;
    private Long responseTime;
}
