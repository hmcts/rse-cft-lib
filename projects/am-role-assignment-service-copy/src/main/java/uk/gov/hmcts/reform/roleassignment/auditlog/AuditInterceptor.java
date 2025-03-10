package uk.gov.hmcts.reform.roleassignment.auditlog;

import com.launchdarkly.shaded.org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.hmcts.reform.roleassignment.ApplicationParams;
import uk.gov.hmcts.reform.roleassignment.auditlog.aop.AuditContext;
import uk.gov.hmcts.reform.roleassignment.auditlog.aop.AuditContextHolder;
import uk.gov.hmcts.reform.roleassignment.domain.model.MutableHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuditInterceptor.class);

    public static final String REQUEST_ID = "request-id";

    private final AuditService auditService;
    private final ApplicationParams applicationParams;

    public AuditInterceptor(AuditService auditService, ApplicationParams applicationParams) {
        this.auditService = auditService;
        this.applicationParams = applicationParams;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request,
                                @NotNull HttpServletResponse response, @NotNull Object handler,
                                @Nullable Exception ex) {
        if (applicationParams.isAuditLogEnabled() && hasAuditAnnotation(handler)) {
            if (!applicationParams.getAuditLogIgnoreStatuses().contains(response.getStatus())) {
                var auditContext = AuditContextHolder.getAuditContext();
                auditContext = populateHttpSemantics(auditContext, request, response);
                try {
                    auditService.audit(auditContext);
                } catch (Exception e) {  // Ignoring audit failures
                    LOG.error("Error while auditing the request data:{}", e.getMessage());
                }
            }
            AuditContextHolder.remove();
        }
    }

    private boolean hasAuditAnnotation(Object handler) {
        return handler instanceof HandlerMethod && ((HandlerMethod) handler).hasMethodAnnotation(LogAudit.class);
    }

    private AuditContext populateHttpSemantics(AuditContext auditContext,
                                               HttpServletRequest request, HttpServletResponse response) {
        AuditContext context = (auditContext != null) ? auditContext : new AuditContext();
        context.setHttpStatus(response.getStatus());
        context.setHttpMethod(request.getMethod());
        context.setRequestPath(request.getRequestURI());
        if ((context.getResponseTime() != null && context.getResponseTime() > 500)
            || context.getHttpStatus() == 422
            || LOG.isDebugEnabled()) {
            context.setRequestPayload(new MutableHttpServletRequest(request).getBodyAsString());
        }
        return context;
    }
}
