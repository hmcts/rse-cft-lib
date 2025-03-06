package uk.gov.hmcts.reform.roleassignment.auditlog.aop;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.framework.DefaultAopProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType;
import uk.gov.hmcts.reform.roleassignment.auditlog.LogAudit;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.GET_ASSIGNMENTS_BY_ACTOR;

class AuditAspectTest {
    private static final String ACTOR_ID = "6b36bfc6-bb21-11ea-b3de-0242ac130004";
    private static final String ID = "56a95928-6fd9-42b4-afda-12b6c69f1036";
    private static final String ROLE_NAME = "Judge";
    private final AuditAspect aspect = new AuditAspect();
    private TestController controllerProxy;

    @BeforeEach
    public void setUp() {
        AspectJProxyFactory aspectJProxyFactory = new AspectJProxyFactory(new TestController());
        aspectJProxyFactory.addAspect(aspect);

        DefaultAopProxyFactory proxyFactory = new DefaultAopProxyFactory();
        AopProxy aopProxy = proxyFactory.createAopProxy(aspectJProxyFactory);

        controllerProxy = (TestController) aopProxy.getProxy();
    }

    @Test
    void shouldPopulateAuditContext() {
        RoleAssignment result = controllerProxy.retrieveRoleAssignmentByActorId(ACTOR_ID, ROLE_NAME);
        assertThat(result).isNotNull();

        AuditContext context = AuditContextHolder.getAuditContext();

        assertThat(context).isNotNull();

        assertThat(context.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(context.getAuditOperationType()).isEqualTo(GET_ASSIGNMENTS_BY_ACTOR);
        assertThat(context.getAssignmentId()).isEqualTo(ID);
        assertThat(context.getAssignmentSize()).isEqualTo(1);
        assertThat(context.getRoleName()).isEqualTo(ROLE_NAME);
        assertThat(context.getResponseTime()).isNotNull();
        assertThat(context.getRequestPayload()).isNull();

    }

    @Test
    void shouldThrowExceptionOnInvalidMethod() {
        controllerProxy.retrieveRoleAssignmentByActorId_invalidMethod(ACTOR_ID, ROLE_NAME);
        AuditContext context = AuditContextHolder.getAuditContext();
        assertThat(context).isNotNull();
    }

    @Test
    void unProcessableAuditContext() {
        for (HttpStatus state: List.of(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.CONFLICT)) {
            ResponseEntity<?> result = controllerProxy.responseProcessEntity(state, ACTOR_ID, ROLE_NAME);
            assertThat(result).isNotNull();
            assertThat(((RoleAssignment) Objects.requireNonNull(result.getBody())).getRoleName()).isEqualTo(ROLE_NAME);
        }
    }

    @Test
    void acceptProcessableAuditContext() {
        ResponseEntity<?> result = controllerProxy.responseProcessEntity(HttpStatus.ACCEPTED, ACTOR_ID, ROLE_NAME);
        assertThat(result).isNotNull();
        assertThat(((RoleAssignment) Objects.requireNonNull(result.getBody())).getRoleName()).isEqualTo(ROLE_NAME);
    }



    @Controller
    @SuppressWarnings("unused")
    public static class TestController {

        public static final String JURISDICTION = "PROBATE";

        @LogAudit(operationType = AuditOperationType.GET_ASSIGNMENTS_BY_ACTOR, actorId
            = "#actorId", id = "#result.id", roleName = "#roleName", size = "#result.log")
        public RoleAssignment retrieveRoleAssignmentByActorId(String actorId, String roleName) {

            return RoleAssignment.builder()
                .id(UUID.fromString(ID))
                .log("1")
                .roleName(roleName).build();
        }

        @LogAudit(operationType = AuditOperationType.GET_ASSIGNMENTS_BY_ACTOR, actorId
            = "#actorId", id = "#result.invalidMethod", roleName = "#roleName")
        public void retrieveRoleAssignmentByActorId_invalidMethod(String actorId, String roleName) {
        }

        @LogAudit(operationType = AuditOperationType.CREATE_ASSIGNMENTS,
            process = "#process",
            reference = "#reference",
            actorId = "#actorId")
        public RoleAssignment createRoleAssignment(String process, String reference, String actorId) {
            throw new RuntimeException("get RoleAssignment failed");
        }

        @LogAudit(operationType = AuditOperationType.CREATE_ASSIGNMENTS,
            process = "#process",
            reference = "#reference",
            actorId = "#actorId")
        public ResponseEntity<?> responseProcessEntity(HttpStatus state, String actorId, String roleName) {
            RoleAssignment roleAssignment = RoleAssignment.builder()
                .id(UUID.fromString(ID))
                .roleName(roleName).build();

            return ResponseEntity.status(state).body(roleAssignment);
        }

    }
}
