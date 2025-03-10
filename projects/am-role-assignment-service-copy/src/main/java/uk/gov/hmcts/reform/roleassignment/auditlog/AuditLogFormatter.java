package uk.gov.hmcts.reform.roleassignment.auditlog;

import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class AuditLogFormatter {

    public static final String TAG = "LA-AM-RAS";

    private static final String COMMA = ",";
    private static final String COLON = ":";

    public String format(AuditEntry entry) {
        return new StringBuilder(TAG)
            .append(" ")
            .append(getFirstPair("dateTime", entry.getDateTime()))
            .append(getPair("operationType", entry.getOperationType()))
            .append(getPair("assignerId", entry.getAssignerId()))
            .append(getPair("assignmentId", entry.getAssignmentId()))
            .append(getPair("assignmentSize", String.valueOf(entry.getAssignmentSize())))
            .append(getPair("invokingService", entry.getInvokingService()))
            .append(getPair("endpointCalled", entry.getPath()))
            .append(getPair("operationalOutcome", String.valueOf(entry.getHttpStatus())))
            .append(getPair("actorId", entry.getActorId()))
            .append(getPair("process", entry.getProcess()))
            .append(getPair("reference", entry.getReference()))
            .append(getPair("roleName", entry.getRoleName()))
            .append(getPair("authenticatedUserId", entry.getAuthenticatedUserId()))
            .append(getPair("correlationId", entry.getCorrelationId()))
            .append(getPair("requestPayload", entry.getRequestPayload()))
            .append(getPair("responseTime",String.valueOf(entry.getResponseTime())))
            .toString();
    }

    private String getPair(String label, String value) {
        return isNotBlank(value) ? COMMA + label + COLON + value : "";
    }

    private String getFirstPair(String label, String value) {
        return isNotBlank(value) ? label + COLON + value : "";
    }

}
