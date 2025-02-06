package uk.gov.hmcts.reform.roleassignment.auditlog.aop;

import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.roleassignment.auditlog.LogAudit;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
@ConditionalOnProperty(prefix = "audit.log", name = "enabled", havingValue = "true")
public class AuditAspect {

    private static final Logger LOG = LoggerFactory.getLogger(AuditAspect.class);

    private static final String RESULT_VARIABLE = "result";
    private ExpressionEvaluator evaluator = new ExpressionEvaluator();

    List<Integer> statusCodes = Arrays.asList(409, 422);

    @Around("@annotation(logAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, LogAudit logAudit) throws Throwable {

        var startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();

        if (result instanceof ResponseEntity && statusCodes.contains(((ResponseEntity) result).getStatusCodeValue())) {
            return result;
        } else {
            var roleName = getValue(joinPoint, logAudit.roleName(), result, String.class);
            var assignerId = getValue(joinPoint, logAudit.assignerId(), result, String.class);
            var id = getValue(joinPoint, logAudit.id(), result, String.class);
            var size = getValue(joinPoint, logAudit.size(), result, Integer.class);
            var actorId = getValue(joinPoint, logAudit.actorId(), result, String.class);
            var process = getValue(joinPoint, logAudit.process(), result, String.class);
            var reference = getValue(joinPoint, logAudit.reference(), result, String.class);
            var correlationId = getValue(joinPoint, logAudit.correlationId(), result, String.class);
            var responseTime = Math.subtractExact(System.currentTimeMillis(), startTime);

            AuditContextHolder.setAuditContext(AuditContext.auditContextWith()
                                                   .auditOperationType(logAudit.operationType())
                                                   .roleName(roleName)
                                                   .assignerId(assignerId)
                                                   .assignmentId(id)
                                                   .actorId(actorId)
                                                   .process(process)
                                                   .reference(reference)
                                                   .correlationId(correlationId)
                                                   .assignmentSize(size)
                                                   .responseTime(responseTime)
                                                   .build());
        }


        return result;
    }

    private <T> T getValue(JoinPoint joinPoint, String condition, Object result, Class<T> returnType) {
        if (StringUtils.isNotBlank(condition) && !(result == null && condition.contains(RESULT_VARIABLE))) {
            var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            try {
                var evaluationContext = evaluator.createEvaluationContext(joinPoint.getThis(),
                                                                                        joinPoint.getThis().getClass(),
                                                                                        method, joinPoint.getArgs()
                );
                evaluationContext.setVariable(RESULT_VARIABLE, result);
                var methodKey = new AnnotatedElementKey(method, joinPoint.getThis().getClass());
                return evaluator.condition(condition, methodKey, evaluationContext, returnType);
            } catch (SpelEvaluationException ex) {
                LOG.warn("Error evaluating LogAudit annotation expression:{} on method:{}",
                         condition, method.getName(), ex
                );
                return null;
            }
        }
        return null;
    }
}
