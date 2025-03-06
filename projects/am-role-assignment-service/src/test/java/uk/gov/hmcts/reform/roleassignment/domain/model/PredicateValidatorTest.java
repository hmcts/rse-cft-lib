package uk.gov.hmcts.reform.roleassignment.domain.model;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredicateValidatorTest {

    @Test
    void stringCheckPredicate() {
        assertTrue(PredicateValidator.stringCheckPredicate(RoleType.CASE.name()).test("CASE"));
    }

    @Test
    void assignmentRequestPredicate() {
        assertTrue(PredicateValidator.assignmentRequestPredicate(Status.valueOf("REJECTED"))
            .test(Status.REJECTED));
    }

    @Test
    void stringCheckPredicate_Fail() {
        assertFalse(PredicateValidator.stringCheckPredicate(RoleType.CASE.name()).test("CA"));
    }

    @Test
    void assignmentRequestPredicate_Fail() {
        assertFalse(PredicateValidator.assignmentRequestPredicate(Status.valueOf("CREATED"))
                       .test(Status.REJECTED));
    }
}
