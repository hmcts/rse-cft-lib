package uk.gov.hmcts.reform.roleassignment.auditlog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import uk.gov.hmcts.reform.roleassignment.ApplicationParams;
import uk.gov.hmcts.reform.roleassignment.auditlog.aop.AuditContext;
import uk.gov.hmcts.reform.roleassignment.auditlog.aop.AuditContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditInterceptorTest {

    private static final int STATUS = 200;
    private static final String METHOD = "GET";
    private static final String REQUEST_URI = "/cases/1234";
    private static final String REQUEST_ID = "tes_request_id";
    private AuditContext auditContextSpy;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpServletResponse responseNew;

    @Captor
    private ArgumentCaptor<ILoggingEvent> captor;

    private AuditInterceptor interceptor;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationParams applicationParams;
    @Mock
    private HandlerMethod handler;
    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new AuditInterceptor(auditService, applicationParams);

        request = new MockHttpServletRequest(METHOD, REQUEST_URI);
        request.addHeader(AuditInterceptor.REQUEST_ID, REQUEST_ID);
        response = new MockHttpServletResponse();
        response.setStatus(STATUS);
        responseNew = new MockHttpServletResponse();
        responseNew.setStatus(422);

        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(mockAppender);
        rootLogger.setLevel(Level.DEBUG);

        given(applicationParams.isAuditLogEnabled()).willReturn(true);
        given(applicationParams.getAuditLogIgnoreStatuses()).willReturn(Lists.newArrayList(404));
    }

    @Test
    void shouldPrepareAuditContextWithHttpSemanticsLongResponse() {
        AuditContext auditContext = new AuditContext();
        auditContext.setResponseTime(1500L);

        auditContextSpy = spy(auditContext);
        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(true);

        AuditContextHolder.setAuditContext(auditContextSpy);
        interceptor.afterCompletion(request, response, handler, null);

        assertNotNull(auditContext);
        assertThat(handler).isInstanceOf(HandlerMethod.class);
        assertThat(auditContextSpy.getHttpMethod()).isEqualTo(METHOD);
        assertThat(auditContextSpy.getRequestPath()).isEqualTo(REQUEST_URI);
        assertThat(auditContextSpy.getHttpStatus()).isEqualTo(STATUS);
        assertThat(AuditContextHolder.getAuditContext()).isNull();
        assertNotNull(auditContextSpy.getResponseTime());
        assertThat(auditContextSpy.getResponseTime()).isGreaterThan(500L);
        assertThat(auditContextSpy.getRequestPayload()).isEmpty();
        verify(auditContextSpy, times(1)).setRequestPayload(any());
        verify(auditService).audit(auditContextSpy);

    }

    @Test
    void shouldPrepareAuditContextWithHttpSemanticsShortResponse() {
        AuditContext auditContext = new AuditContext();
        auditContext.setResponseTime(400L);

        auditContextSpy = spy(auditContext);
        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(true);
        AuditContextHolder.setAuditContext(auditContextSpy);
        interceptor.afterCompletion(request, response, handler, null);
        assertNotNull(auditContext);
        assertThat(auditContextSpy.getHttpMethod()).isEqualTo(METHOD);
        assertThat(auditContextSpy.getRequestPath()).isEqualTo(REQUEST_URI);
        assertThat(auditContextSpy.getHttpStatus()).isEqualTo(STATUS);
        assertThat(auditContextSpy.getRequestPayload()).isEmpty();
        assertThat(auditContextSpy.getResponseTime()).isNotNull();
        assertThat(AuditContextHolder.getAuditContext()).isNull();
        assertThat(auditContextSpy.getResponseTime()).isLessThan(500L);
        assertThat(auditContextSpy.getResponseTime()).isGreaterThan(1L);
        verify(auditContextSpy, times(1)).setRequestPayload(any());
        verify(auditService).audit(auditContextSpy);

    }

    @Test
    void shouldPrepareAuditContextWithHttpSemanticsOnResponse422() {
        AuditContext auditContext = new AuditContext();
        auditContextSpy = spy(auditContext);

        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(true);
        assertThat(handler).isInstanceOf(HandlerMethod.class);
        AuditContextHolder.setAuditContext(auditContextSpy);
        interceptor.afterCompletion(request, responseNew, handler, null);
        assertNotNull(auditContext);
        assertThat(auditContextSpy.getHttpMethod()).isEqualTo(METHOD);
        assertThat(auditContextSpy.getRequestPath()).isEqualTo(REQUEST_URI);
        assertThat(auditContextSpy.getHttpStatus()).isEqualTo(422);
        verify(auditContextSpy, times(1)).setRequestPayload(any());
        verify(auditService).audit(auditContextSpy);

    }

    @Test
    void shouldCheckIfDebugEnabled() {
        AuditContext auditContext = new AuditContext();
        auditContextSpy = spy(auditContext);
        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(true);
        AuditContextHolder.setAuditContext(auditContextSpy);
        interceptor.afterCompletion(request, responseNew, handler, null);
        verify(auditService).audit(auditContextSpy);


    }


    @Test
    void shouldNotAuditForWhenAnnotationIsNotPresent() {

        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(false);
        assertThat(handler).isInstanceOf(HandlerMethod.class);
        interceptor.afterCompletion(request, response, handler, null);

        verifyNoInteractions(auditService);

    }

    @Test
    void shouldNotAuditFor404Status() {
        response.setStatus(404);
        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(true);
        assertThat(handler).isInstanceOf(HandlerMethod.class);
        interceptor.afterCompletion(request, response, handler, null);

        verifyNoInteractions(auditService);

    }

    @Test
    void shouldClearAuditContextAlways() {

        AuditContext auditContext = new AuditContext();
        AuditContextHolder.setAuditContext(auditContext);

        given(handler.hasMethodAnnotation(LogAudit.class)).willReturn(true);
        assertThat(handler).isInstanceOf(HandlerMethod.class);
        doThrow(new RuntimeException("audit failure")).when(auditService).audit(auditContext);

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(AuditContextHolder.getAuditContext()).isNull();
    }

    @Test
    void shouldNotAuditIfDisabled() {

        given(applicationParams.isAuditLogEnabled()).willReturn(false);

        interceptor.afterCompletion(request, response, handler, null);

        verifyNoInteractions(auditService);

    }
}
