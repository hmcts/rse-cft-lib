package uk.gov.hmcts.reform.roleassignment.domain.model;

import java.util.function.Predicate;

import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

public class PredicateValidator {

    private PredicateValidator() {
    }

    public static Predicate<String> stringCheckPredicate(String value) {

        return name -> name.equalsIgnoreCase(value);
    }

    public static Predicate<Status> assignmentRequestPredicate(Status assignmentRequestStatus) {

        return status -> status.equals(assignmentRequestStatus);
    }

}
