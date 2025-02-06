package uk.gov.hmcts.reform.roleassignment.util;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATED;

class AuditLoggerUtilTest {

    private AssignmentRequest assignmentRequest;
    private RoleAssignmentRequestResource roleAssignmentRequestResource;
    private ResponseEntity<RoleAssignmentRequestResource> responseEntity;
    private ResponseEntity<RoleAssignmentResource> roleAssignmentResponseEntity;
    private RoleAssignmentResource roleAssignmentResource;

    @BeforeEach
    public void setUp() throws IOException {
        assignmentRequest = TestDataBuilder.buildAssignmentRequest(CREATED, CREATED,
                                                                   false
        );

        roleAssignmentRequestResource = new RoleAssignmentRequestResource(assignmentRequest);
        responseEntity = ResponseEntity.ok(roleAssignmentRequestResource);
        roleAssignmentResource = new RoleAssignmentResource();
        roleAssignmentResource.setRoleAssignmentResponse((List<? extends Assignment>) assignmentRequest
            .getRequestedRoles());
        roleAssignmentResponseEntity = ResponseEntity.ok(roleAssignmentResource);


    }

    @Test
    void checkAssignmentIds() {

        List<UUID> expectedIds = Arrays.asList(
            UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f"),
            UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f")
        );

        List<UUID> assignmentIds = AuditLoggerUtil.buildAssignmentIds(responseEntity);
        assertNotNull(assignmentIds);
        assertThat(assignmentIds).isEqualTo(expectedIds);
    }

    @Test
    void checkAssignmentIdsNullResponse() {
        List<UUID> assignmentIds = AuditLoggerUtil.buildAssignmentIds(ResponseEntity.ok().build());
        assertEquals(0, assignmentIds.size());
    }

    @Test
    void checkActorIds() {

        Set<String> expectedIds = Set.of(
            "21334a2b-79ce-44eb-9168-2d49a744be9c"
        );

        Set<String> actorIds = AuditLoggerUtil.buildActorIds(responseEntity);
        assertNotNull(actorIds);
        assertThat(actorIds).isEqualTo(expectedIds);
    }

    @Test
    void checkActorIdsNullResponse() {
        Set<String> actorIds = AuditLoggerUtil.buildActorIds(ResponseEntity.ok().build());
        assertEquals(0, actorIds.size());
    }

    @Test
    void checkRoleNames() {

        List<String> expectedRoles = Arrays.asList(
            "judge",
            "judge"
        );

        List<String> roleNames = AuditLoggerUtil.buildRoleNames(responseEntity);
        assertNotNull(roleNames);
        assertThat(roleNames).isEqualTo(expectedRoles);
    }

    @Test
    void checkRoleNamesEmpty() {
        List<String> roleNames = AuditLoggerUtil.buildRoleNames(ResponseEntity.ok().build());
        assertEquals(0, roleNames.size());
    }

    @Test
    void checkCaseIds() {

        Set<String> expectedCaseIds = new HashSet<>();
        expectedCaseIds.add("1234567890123456");

        Set<String> caseIds = AuditLoggerUtil.buildCaseIds(responseEntity);
        assertNotNull(caseIds);
        assertThat(caseIds).isEqualTo(expectedCaseIds);
    }

    @Test
    void checkCaseIdsEmpty() {
        Set<String> caseIds = AuditLoggerUtil.buildCaseIds(ResponseEntity.ok().build());
        assertEquals(0, caseIds.size());
    }

    @Test
    void shouldReturnAssignmentIds() {

        List<UUID> expectedIds = Arrays.asList(
            UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f"),
            UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f")
        );

        List<UUID> assignmentIds = AuditLoggerUtil.getAssignmentIds(roleAssignmentResponseEntity);
        assertNotNull(assignmentIds);
        assertThat(assignmentIds).isEqualTo(expectedIds);
    }

    @Test
    void shouldReturnEmptyAssignmentIds() {
        List<UUID> assignmentIds = AuditLoggerUtil.getAssignmentIds(ResponseEntity.ok().build());
        assertEquals(0, assignmentIds.size());
    }

    @Test
    void shouldReturnActorIds() {

        Set<String> expectedActorIds = Set.of(
            "21334a2b-79ce-44eb-9168-2d49a744be9c");

        Set<String> actorIds = AuditLoggerUtil.getActorIds(roleAssignmentResponseEntity);
        assertNotNull(actorIds);
        assertThat(actorIds).isEqualTo(expectedActorIds);
    }

    @Test
    void shouldReturnEmptyActorIds() {
        Set<String> actorIds = AuditLoggerUtil.getActorIds(ResponseEntity.ok().build());
        assertEquals(0, actorIds.size());
    }

    @Test
    void shouldReturnAssignmentIdsForSearch() {
        List<UUID> expectedIds = Arrays.asList(
            UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f"),
            UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f")
        );

        ResponseEntity<RoleAssignmentResource> responseEntity = ResponseEntity
            .ok(new RoleAssignmentResource((List<? extends Assignment>) assignmentRequest.getRequestedRoles()));
        List<UUID> assignmentIds = AuditLoggerUtil.searchAssignmentIds(responseEntity);
        assertNotNull(assignmentIds);
        assertThat(assignmentIds).isEqualTo(expectedIds);
    }

    @Test
    void shouldReturnEmptyAssignmentIdsForSearch() {
        List<UUID> assignmentIds = AuditLoggerUtil.searchAssignmentIds(ResponseEntity.ok().build());
        assertEquals(0, assignmentIds.size());
    }

    @Test
    void shouldReturnSizeOfAssignments() {
        ResponseEntity<RoleAssignmentResource> responseEntity = ResponseEntity
            .ok(new RoleAssignmentResource((List<? extends Assignment>) assignmentRequest.getRequestedRoles()));
        String assignmentSize = AuditLoggerUtil.sizeOfAssignments(responseEntity);
        assertNotNull(assignmentSize);
        assertThat(assignmentSize).isEqualTo("2");
    }

    @Test
    void shouldReturnNullAsSizeOfAssignments() {
        ResponseEntity<RoleAssignmentResource> responseEntity = ResponseEntity.ok().build();
        String assignmentSize = AuditLoggerUtil.sizeOfAssignments(responseEntity);
        assertNull(assignmentSize);
    }
}
