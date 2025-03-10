package uk.gov.hmcts.reform.roleassignment.auditlog;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.roleassignment.auditlog.aop.AuditContext;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("audit log specific calls")
class AuditServiceTest {

    private static final String TARGET_IDAM_ID = "target@mail.com";
    private static final String SERVICE_NAME = "ccd_api_gateway";
    private static final String USER_ID = "123e4567-e89b-42d3-a456-556642445555";
    private static final String REQUEST_ID_VALUE = "30f14c6c1fc85cba12bfd093aa8f90e3";
    private static final String PATH = "/someUri";
    private static final String HTTP_METHOD = "POST";
    private static final String JURISDICTION = "AUTOTEST1";
    private static final String CASE_TYPE = "CaseType1";
    private static final String EVENT_NAME = "CreateCase";
    private static final List<String> TARGET_CASE_ROLES = Arrays.asList("CaseRole1", "CaseRole2");
    public static final String ACTOR_ID = "ADCED";
    public static final String PROCESS_ID = "39289489";
    public static final String REFERENCE_ID = "DR_DKGRO";
    public static final String ASSIGNER_ID = "DE_WQRP";
    public static final String ASSIGNMENT_ID = "DF_59895";
    public static final String ROLE_NAME = "ADMIN";
    public static final String CORRELATION_ID = "CORRELATION-1";
    public static final String REQUEST_PAYLOAD = "PAYLOAD-1";
    public static final Long RESPONSE_TIME = 500L;


    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private AuditRepository auditRepository;

    @Captor
    ArgumentCaptor<AuditEntry> captor;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2018-08-19T16:02:42.01Z"), ZoneOffset.UTC);

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditService = new AuditService(fixedClock, securityUtils, auditRepository);

        doReturn(SERVICE_NAME).when(securityUtils).getServiceName();
        doReturn(USER_ID).when(securityUtils).getUserId();
    }

    @Test
    @DisplayName("should save to audit repository")
    void shouldSaveToAuditRepository() {
        AuditContext auditContext = AuditContext.auditContextWith()
            .auditOperationType(AuditOperationType.CREATE_ASSIGNMENTS)
            .jurisdiction(JURISDICTION)
            .caseType(CASE_TYPE)
            .eventName(EVENT_NAME)
            .targetIdamId(TARGET_IDAM_ID)
            .targetCaseRoles(TARGET_CASE_ROLES)
            .httpMethod(HTTP_METHOD)
            .httpStatus(200)
            .requestPath(PATH)
            .requestId(REQUEST_ID_VALUE)
            .actorId(ACTOR_ID)
            .process(PROCESS_ID)
            .reference(REFERENCE_ID)
            .assignerId(ASSIGNER_ID)
            .assignmentId(ASSIGNMENT_ID)
            .roleName(ROLE_NAME)
            .correlationId(CORRELATION_ID)
            .requestPayload(REQUEST_PAYLOAD)
            .assignmentSize(1)
            .responseTime(RESPONSE_TIME)
            .build();
        AuditContext auditContextSpy = Mockito.spy(auditContext);
        auditService.audit(auditContextSpy);

        verify(auditRepository).save(captor.capture());
        verify(securityUtils).getUserId();
        verify(auditContextSpy, times(1)).getRequestPayload();
        AuditEntry entry = captor.getValue();

        assertThat(entry.getDateTime(), is(equalTo("2018-08-19T16:02:42.01")));
        assertThat(entry.getHttpStatus(), is(equalTo(200)));
        assertThat(entry.getHttpMethod(), is(equalTo(HTTP_METHOD)));
        assertThat(entry.getPath(), is(equalTo((PATH))));
        assertThat(entry.getActorId(), is(equalTo(auditContextSpy.getActorId())));
        assertThat(entry.getProcess(), is(equalTo(auditContextSpy.getProcess())));
        assertThat(entry.getReference(), is(equalTo(auditContextSpy.getReference())));
        assertThat(entry.getAssignerId(), is(equalTo(auditContextSpy.getAssignerId())));
        assertThat(entry.getAssignmentId(), is(equalTo(auditContextSpy.getAssignmentId())));
        assertThat(entry.getRoleName(), is(equalTo(auditContextSpy.getRoleName())));
        assertThat(entry.getCorrelationId(), is(equalTo(auditContextSpy.getCorrelationId())));
        assertThat(entry.getAuthenticatedUserId(), is(equalTo(securityUtils.getUserId())));
        assertThat(entry.getResponseTime(), is(equalTo(auditContextSpy.getResponseTime())));
        assertThat(entry.getInvokingService(), is(equalTo((SERVICE_NAME))));
        assertThat(entry.getOperationType(), is(equalTo(AuditOperationType.CREATE_ASSIGNMENTS.getLabel())));
        assertThat(entry.getRequestPayload(), is(equalTo(auditContextSpy.getRequestPayload())));
        assertThat(entry.getAssignmentSize(), is(equalTo(1)));
    }

    @Test
    @DisplayName("should save to audit repository")
    void shouldSaveToAuditRepositoryWithNullOperationType() {
        AuditContext auditContext = AuditContext.auditContextWith()
            .auditOperationType(null)
            .httpStatus(403)
            .build();

        auditService.audit(auditContext);

        verify(auditRepository).save(captor.capture());

        assertThat(captor.getValue().getHttpStatus(), is(equalTo(403)));
        Assert.assertNull(captor.getValue().getOperationType());
    }
}
