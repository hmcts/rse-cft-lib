package uk.gov.hmcts.reform.roleassignment.auditlog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this annotation on the endpoint method to create the audit log entry and send to stdout.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface LogAudit {

    AuditOperationType operationType();

    String roleName() default "";

    String assignerId() default "";

    String id() default "";

    String actorId() default "";

    String caseId() default "";

    String process() default "";

    String reference() default "";

    String assignmentId() default "";

    String correlationId() default "";

    String requestPayload() default "";

    String size() default "";
}
