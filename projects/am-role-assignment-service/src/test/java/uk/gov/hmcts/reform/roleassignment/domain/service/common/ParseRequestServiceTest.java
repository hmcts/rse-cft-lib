package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.util.CorrelationInterceptorUtil;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATED;

@RunWith(MockitoJUnitRunner.class)
class ParseRequestServiceTest {

    @InjectMocks
    private ParseRequestService sut = new ParseRequestService();

    @Mock
    private SecurityUtils securityUtilsMock = mock(SecurityUtils.class);

    @Mock
    private CorrelationInterceptorUtil correlationInterceptorUtilMock = mock(CorrelationInterceptorUtil.class);

    private static final String ROLE_TYPE = "CASE";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("should throw 400 exception for a syntactically bad Assignment id")
    void shouldThrowBadRequestForMalformedAssignmentId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String assignmentId = "you_cant_see_this_malformed_id";
        UUID userId = UUID.fromString("21334a2b-79ce-44eb-9168-2d49a744be9c");
        when(securityUtilsMock.getUserId()).thenReturn(userId.toString());
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.prepareDeleteRequest(null, null, null, assignmentId)
        );
    }

    @Test
    void shouldReturn400IfRoleTypeIsNotCaseForGetRoleAssignmentByActorIdAndCaseId() {
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        String roleType = "SomeFakeCaseType";
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.validateGetAssignmentsByActorIdAndCaseId(actorId, null, roleType)
        );
    }

    @Test
    void getRoleAssignment_emptyCaseIdThrows400() {
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.validateGetAssignmentsByActorIdAndCaseId(actorId, null, null)
        );
    }

    @Test
    void getRoleAssignmentByActorAndCaseId_shouldThrowBadRequestWhenActorAndCaseIdIsEmpty() {

        Assertions.assertThrows(BadRequestException.class, () ->
            sut.validateGetAssignmentsByActorIdAndCaseId(null, null, ROLE_TYPE)
        );
    }

    @Test
    void getRoleAssignmentThrow400ForInvalidCaseId() {
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        String caseId = "%%%-e89b-42d3-a456-556642445678";

        Assertions.assertThrows(BadRequestException.class, () ->
            sut.validateGetAssignmentsByActorIdAndCaseId(actorId, caseId, ROLE_TYPE)
        );
    }

    @Test
    void getRoleAssignmentByActorAndCaseId_shouldThrowBadRequestWhenActorIsNotUUID() {

        String actorId = "a_bad_uuid";
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.validateGetAssignmentsByActorIdAndCaseId(actorId, null, ROLE_TYPE)
        );
    }

    @Test
    void getCorrelationId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(correlationInterceptorUtilMock.preHandle(
            any(HttpServletRequest.class))).thenReturn("21334a2b-79ce-44eb-9168-2d49a744be9d");
        assertEquals("21334a2b-79ce-44eb-9168-2d49a744be9d", sut.getRequestCorrelationId());
        verify(correlationInterceptorUtilMock, times(1))
            .preHandle(any(HttpServletRequest.class));
    }

    @Test
    void prepareDeleteRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        String clientId = "copied client id";
        UUID userId = UUID.fromString("21334a2b-79ce-44eb-9168-2d49a744be9c");
        when(securityUtilsMock.getServiceName()).thenReturn(clientId);
        when(securityUtilsMock.getUserId()).thenReturn(userId.toString());
        when(correlationInterceptorUtilMock.preHandle(
            any(HttpServletRequest.class))).thenReturn("21334a2b-79ce-44eb-9168-2d49a744be9d");

        Request builtReq = TestDataBuilder.buildRequest(CREATED, false);
        Request result = sut.prepareDeleteRequest(builtReq.getProcess(), builtReq.getReference(),
                                                  "21334a2b-79ce-44eb-9168-2d49a744be9d",
                                                  "21334a2b-79ce-44eb-9168-2d49a744be9d"
        );
        builtReq.setRequestType(RequestType.DELETE);

        assertFalse(result.isByPassOrgDroolRule());
        assertEquals(clientId, result.getClientId());
        assertEquals(userId.toString(), result.getAuthenticatedUserId());
        assertEquals(UUID.fromString("21334a2b-79ce-44eb-9168-2d49a744be9d"), result.getRoleAssignmentId());
        assertEquals(builtReq.getStatus(), result.getStatus());
        assertEquals(builtReq.getRequestType(), result.getRequestType());
        assertEquals(builtReq.getProcess(), result.getProcess());
        assertEquals(builtReq.getReference(), result.getReference());
        assertEquals("21334a2b-79ce-44eb-9168-2d49a744be9c", result.getAssignerId());
        assertEquals("21334a2b-79ce-44eb-9168-2d49a744be9d", result.getCorrelationId());
    }

    @Test
    void prepareDeleteRequest_AssignerIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("assignerId", "21334a2b-79ce-44eb-9168-2d49a744be9c");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        String clientId = "copied client id";
        UUID userId = UUID.fromString("21334a2b-79ce-44eb-9168-2d49a744be9c");
        when(securityUtilsMock.getServiceName()).thenReturn(clientId);
        when(securityUtilsMock.getUserId()).thenReturn(userId.toString());
        when(correlationInterceptorUtilMock.preHandle(
            any(HttpServletRequest.class))).thenReturn("21334a2b-79ce-44eb-9168-2d49a744be9d");

        Request builtReq = TestDataBuilder.buildRequest(CREATED, false);
        Request result = sut.prepareDeleteRequest(builtReq.getProcess(), builtReq.getReference(),
                                                  "21334a2b-79ce-44eb-9168-2d49a744be9d",
                                                  "21334a2b-79ce-44eb-9168-2d49a744be9d"
        );
        builtReq.setRequestType(RequestType.DELETE);

        assertFalse(result.isByPassOrgDroolRule());
        assertEquals(clientId, result.getClientId());
        assertEquals(userId.toString(), result.getAuthenticatedUserId());
        assertEquals(builtReq.getStatus(), result.getStatus());
        assertEquals(builtReq.getRequestType(), result.getRequestType());
        assertEquals(builtReq.getProcess(), result.getProcess());
        assertEquals(builtReq.getReference(), result.getReference());
        assertEquals("21334a2b-79ce-44eb-9168-2d49a744be9c", result.getAssignerId());
        assertEquals("21334a2b-79ce-44eb-9168-2d49a744be9d", result.getCorrelationId());
    }

    @Test
    void prepareDeleteRequest_InvalidAssignerIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("assignerId", "%%");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        String clientId = "copied client id";
        String userId = "21334a2b-79ce-44eb-9168-2d49a744be9";
        when(securityUtilsMock.getServiceName()).thenReturn(clientId);
        when(securityUtilsMock.getUserId()).thenReturn(userId);
        when(correlationInterceptorUtilMock.preHandle(
            any(HttpServletRequest.class))).thenReturn("21334a2b-79ce-44eb-9168-2d49a744be9d");

        Assertions.assertThrows(BadRequestException.class, () ->
            sut.prepareDeleteRequest("p2", "p2",
                                     "21334a2b-79ce-44eb-9168-2d49a744be9d",
                                     "21334a2b-79ce-44eb-9168-2d49a744be9d")
        );
    }

    @Test
    void prepareDeleteRequest_InvalidUuid() {
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.prepareDeleteRequest("p2", "p2",
                                     "21334a2b-79ce-44e$$b-9168-2d49a744be9",
                                     "21334a2b-79ce-44eb-9168-2d49a744be9d")
        );
    }

    @Test
    void parseRequest_CreateEndpoint_HappyPath() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        String clientId = "copied client id";
        String userId = "21334a2b-79ce-44eb-9168-2d49a744be9c";
        when(securityUtilsMock.getServiceName()).thenReturn(clientId);
        when(securityUtilsMock.getUserId()).thenReturn(userId);
        when(correlationInterceptorUtilMock.preHandle(
            any(HttpServletRequest.class))).thenReturn("21334a2b-79ce-44eb-9168-2d49a744be9d");

        RequestType requestType = RequestType.CREATE;
        AssignmentRequest assignmentRequest = TestDataBuilder.buildAssignmentRequest(Status.LIVE, Status.LIVE, false);
        assignmentRequest.getRequest().setCreated(null);
        assignmentRequest.getRequest().setRequestType(null);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> roleAssignment.setCreated(null));
        AssignmentRequest assignmentRequestSpy = Mockito.spy(assignmentRequest);
        AssignmentRequest result = sut.parseRequest(assignmentRequestSpy, requestType);

        sut.removeCorrelationLog();

        assertNotNull(result);
        assertNotNull(result.getRequest());
        assertNotNull(result.getRequestedRoles());
        assertEquals(clientId, result.getRequest().getClientId());
        assertEquals(userId, result.getRequest().getAuthenticatedUserId());
        assertEquals(CREATED, result.getRequest().getStatus());
        assertEquals(requestType, result.getRequest().getRequestType());
        assertNotNull(result.getRequest().getRequestType());
        assertFalse(result.getRequest().isByPassOrgDroolRule());
        assertNotNull(result.getRequest().getCreated());
        assertNotNull(result.getRequestedRoles());
        assertTrue(result.getRequestedRoles().size() > 1);

        result.getRequestedRoles().forEach(requestedRole -> {
            assertEquals(result.getRequest().getProcess(), requestedRole.getProcess());
            assertEquals(result.getRequest().getReference(), requestedRole.getReference());
            assertEquals(Status.CREATE_REQUESTED, requestedRole.getStatus());
            assertNotNull(requestedRole.getCreated());
        });
        verify(assignmentRequestSpy, times(3)).getRequestedRoles();
        verify(securityUtilsMock, times(1)).getServiceName();
        verify(securityUtilsMock, times(1)).getUserId();
        verify(correlationInterceptorUtilMock, times(1))
            .preHandle(any(HttpServletRequest.class));
    }

    @Test
    void parseRequest_CreateEndpoint_ValidationFailAssignmentRequest() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        String clientId = "copied client id";
        UUID userId = UUID.fromString("21334a2b-79ce-44eb-9168-2d49a744be9c");
        when(securityUtilsMock.getServiceName()).thenReturn(clientId);
        when(securityUtilsMock.getUserId()).thenReturn(userId.toString());
        when(correlationInterceptorUtilMock.preHandle(
            any(HttpServletRequest.class))).thenReturn("21334a2b-79ce-44eb-9168-2d49a744be9d");

        AssignmentRequest assignmentRequest = TestDataBuilder.buildAssignmentRequest(CREATED, Status.LIVE, true);
        assignmentRequest.getRequest().setProcess("");

        Assertions.assertThrows(BadRequestException.class, () ->
            sut.parseRequest(assignmentRequest, RequestType.CREATE)
        );
    }
}
