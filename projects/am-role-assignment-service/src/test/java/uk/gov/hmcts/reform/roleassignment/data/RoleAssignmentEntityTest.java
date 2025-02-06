package uk.gov.hmcts.reform.roleassignment.data;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
class RoleAssignmentEntityTest {

    @Test
    void isNew() {
        RoleAssignmentEntity assignmentEntity = new RoleAssignmentEntity();
        assignmentEntity.setNewFlag(true);
        assertTrue(assignmentEntity.isNew());

        assignmentEntity.setNewFlag(false);
        assertFalse(assignmentEntity.isNew());
    }

}
