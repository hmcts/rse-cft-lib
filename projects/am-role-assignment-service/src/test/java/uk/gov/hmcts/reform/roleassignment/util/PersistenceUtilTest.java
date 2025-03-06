package uk.gov.hmcts.reform.roleassignment.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.data.FlagConfig;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.FlagRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@RunWith(MockitoJUnitRunner.class)
class PersistenceUtilTest {

    @InjectMocks
    PersistenceUtil persistenceUtil = new PersistenceUtil();


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void convertRoleAssignmentToHistoryEntity() throws IOException {
        assertNotNull(persistenceUtil.convertRoleAssignmentToHistoryEntity(
            TestDataBuilder.buildRoleAssignment(Status.LIVE),
            TestDataBuilder.buildRequestEntity(TestDataBuilder.buildRequest(Status.APPROVED, false))
        ));
    }

    @Test
    void convertRoleAssignmentWithNullAttributesToHistoryEntity() throws IOException {


        RoleAssignment roleAssignment = TestDataBuilder.buildRoleAssignment(Status.LIVE);
        roleAssignment.setBeginTime(null);
        roleAssignment.setEndTime(null);
        roleAssignment.setAuthorisations(Collections.emptyList());
        HistoryEntity historyEntity = persistenceUtil.convertRoleAssignmentToHistoryEntity(
            roleAssignment,
            TestDataBuilder.buildRequestEntity(TestDataBuilder
                                                   .buildRequest(Status.APPROVED, false))
        );
        assertNotNull(historyEntity);
        assertNull(historyEntity.getBeginTime());
        assertNull(historyEntity.getEndTime());
        assertNull(historyEntity.getAuthorisations());

    }

    @Test
    void convertRequestToEntity() {
        assertNotNull(persistenceUtil.convertRequestToEntity(TestDataBuilder.buildRequest(Status.APPROVED, false)));
    }

    @Test
    void convertRoleAssignmentToEntity() throws IOException {
        RoleAssignment assignment = TestDataBuilder.buildRoleAssignment(Status.LIVE);
        assignment.setAuthorisations(Collections.singletonList("ADMIN"));
        RoleAssignmentEntity entity = persistenceUtil.convertRoleAssignmentToEntity(assignment, true);
        assertNotNull(entity.getAuthorisations());

        assignment.setAuthorisations(Collections.emptyList());
        entity = persistenceUtil.convertRoleAssignmentToEntity(assignment, true);
        assertNull(entity.getAuthorisations());
    }

    @Test
    void convertRoleAssignmentWithNullValuesToEntity() throws IOException {
        RoleAssignment roleAssignment = TestDataBuilder.buildRoleAssignment(Status.LIVE);
        roleAssignment.setBeginTime(null);
        roleAssignment.setEndTime(null);
        roleAssignment.setActorIdType(null);
        roleAssignment.setClassification(null);
        roleAssignment.setGrantType(null);
        roleAssignment.setRoleType(null);
        roleAssignment.setRoleCategory(null);
        RoleAssignmentEntity roleAssignmentEntity = persistenceUtil.convertRoleAssignmentToEntity(
            roleAssignment,
            true
        );
        assertNotNull(roleAssignmentEntity);
        assertNull(roleAssignmentEntity.getBeginTime());
        assertNull(roleAssignmentEntity.getEndTime());
        assertNull(roleAssignmentEntity.getActorIdType());
        assertNull(roleAssignmentEntity.getClassification());
        assertNull(roleAssignmentEntity.getGrantType());
        assertNull(roleAssignmentEntity.getRoleType());
        assertNull(roleAssignmentEntity.getRoleCategory());
    }

    @Test
    void convertHistoryEntityToRoleAssignment() throws IOException {
        final HistoryEntity historyEntity = TestDataBuilder.buildHistoryEntity(
            TestDataBuilder.buildRoleAssignment(Status.LIVE),
            TestDataBuilder.buildRequestEntity(TestDataBuilder.buildRequest(Status.APPROVED, false))
        );

        RoleAssignment roleAssignment = persistenceUtil.convertHistoryEntityToRoleAssignment(historyEntity);
        assertNotNull(roleAssignment);
        assertNotEquals(0, historyEntity.getAuthorisations().length);

        historyEntity.setAuthorisations(new String[]{});
        roleAssignment = persistenceUtil.convertHistoryEntityToRoleAssignment(historyEntity);
        assertNull(roleAssignment.getAuthorisations());
    }

    @Test
    void convertHistoryEntityWithNullAttributesToRoleAssignment() throws IOException {

        HistoryEntity historyEntity = TestDataBuilder.buildHistoryEntity(
            TestDataBuilder.buildRoleAssignment(Status.LIVE),
            TestDataBuilder.buildRequestEntity(TestDataBuilder.buildRequest(Status.APPROVED, false))
        );
        historyEntity.setBeginTime(null);
        historyEntity.setEndTime(null);
        historyEntity.setAuthorisations(null);

        RoleAssignment roleAssignment = persistenceUtil.convertHistoryEntityToRoleAssignment(historyEntity);
        assertNotNull(roleAssignment);
        assertNull(roleAssignment.getEndTime());
        assertNull(roleAssignment.getBeginTime());
        assertNull(roleAssignment.getAuthorisations());
    }

    @Test
    void convertEntityToRoleAssignment() throws IOException {
        assertNotNull(persistenceUtil.convertEntityToRoleAssignment(
            TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(Status.LIVE))));
    }

    @Test
    void convertEntityWithNullAttributesToRoleAssignment() throws IOException {
        RoleAssignmentEntity roleAssignmentEntity = TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder
                                                                                                  .buildRoleAssignment(
                                                                                                      Status.LIVE));
        roleAssignmentEntity.setBeginTime(null);
        roleAssignmentEntity.setEndTime(null);
        RoleAssignment roleAssignment = persistenceUtil.convertEntityToRoleAssignment(roleAssignmentEntity
        );
        assertNotNull(roleAssignment);
        assertNull(roleAssignment.getBeginTime());
        assertNull(roleAssignment.getEndTime());
    }

    @Test
    void convertEntityToRoleAssignmentWithAuthorisations() throws IOException {

        RoleAssignmentEntity entity = TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder
                                                                                    .buildRoleAssignment(Status.LIVE));

        String[] str = {"dev", "tester"};
        entity.setAuthorisations(str);
        RoleAssignment roleAssignment = persistenceUtil.convertEntityToRoleAssignment(entity);
        assertNotNull(roleAssignment);

        assertNotNull(roleAssignment.getRoleCategory());

        entity.setRoleCategory(null);
        roleAssignment = persistenceUtil.convertEntityToRoleAssignment(entity);
        assertNull(roleAssignment.getRoleCategory());

        entity.setAuthorisations(null);
        roleAssignment = persistenceUtil.convertEntityToRoleAssignment(entity);
        assertNull(roleAssignment.getAuthorisations());

        entity.setAuthorisations(new String[]{"ADMIN"});
        roleAssignment = persistenceUtil.convertEntityToRoleAssignment(entity);
        assertNotNull(roleAssignment.getAuthorisations());
    }

    @Test
    void persistHistory() throws IOException {
        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(Status.CREATED, Status.LIVE, false);
        RequestEntity requestEntity = TestDataBuilder.buildRequestEntity(assignmentRequest.getRequest());

        HistoryEntity historyEntityResult = persistenceUtil.prepareHistoryEntityForPersistance(
            assignmentRequest.getRequestedRoles().iterator().next(), assignmentRequest.getRequest());

        assertNotNull(historyEntityResult);
        assertNotNull(assignmentRequest.getRequest().getId());
        assertNotNull(historyEntityResult.getRequestEntity().getId());

        assertEquals(assignmentRequest.getRequest().getId(), historyEntityResult.getRequestEntity().getId());
        assertEquals(assignmentRequest.getRequestedRoles().iterator().next().getId(), historyEntityResult.getId());
        for (RoleAssignment requestedRole : assignmentRequest.getRequestedRoles()) {
            assertEquals(requestedRole.getId(), historyEntityResult.getId());
        }
    }

    @Test
    void convertEntityToExistingRoleAssignment() throws IOException {
        RoleAssignmentEntity assignmentEntity =
            TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(Status.LIVE));
        ExistingRoleAssignment existingRoleAssignment =
            persistenceUtil.convertEntityToExistingRoleAssignment(assignmentEntity);
        assertNotNull(existingRoleAssignment);
        assertNull(existingRoleAssignment.getAuthorisations());

        assignmentEntity.setAuthorisations(new String[]{"ADMIN"});
        existingRoleAssignment =
            persistenceUtil.convertEntityToExistingRoleAssignment(assignmentEntity);
        assertNotNull(existingRoleAssignment.getAuthorisations());
    }

    @Test
    void convertEntityWithNullAttributesToExistingRoleAssignment() throws IOException {
        RoleAssignmentEntity roleAssignmentEntity = TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder
                                                                                                  .buildRoleAssignment(
                                                                                                      Status.LIVE));
        roleAssignmentEntity.setRoleCategory(null);
        roleAssignmentEntity.setBeginTime(null);
        roleAssignmentEntity.setEndTime(null);
        roleAssignmentEntity.setAuthorisations(null);
        ExistingRoleAssignment existingRoleAssignment = persistenceUtil.convertEntityToExistingRoleAssignment(
            roleAssignmentEntity);

        assertNotNull(existingRoleAssignment);
        assertNull(existingRoleAssignment.getRoleCategory());
        assertNull(existingRoleAssignment.getBeginTime());
        assertNull(existingRoleAssignment.getEndTime());
        assertNull(existingRoleAssignment.getAuthorisations());
    }

    @Test
    void convertFlagRequestToFlagConfig() {
        FlagRequest flagRequest = FlagRequest.builder()
            .env("pr")
            .flagName("iac_1_1")
            .serviceName("iac")
            .status(Boolean.TRUE)
            .build();

        FlagConfig flagConfig = persistenceUtil.convertFlagRequestToFlagConfig(flagRequest);
        assertNotNull(flagConfig);
        assertEquals("pr", flagConfig.getEnv());
        assertEquals("iac_1_1", flagConfig.getFlagName());
        assertEquals("iac", flagConfig.getServiceName());
        assertEquals(true, flagConfig.getStatus());

    }
}
