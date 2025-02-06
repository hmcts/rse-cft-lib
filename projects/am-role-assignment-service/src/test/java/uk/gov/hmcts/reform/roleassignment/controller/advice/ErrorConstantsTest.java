package uk.gov.hmcts.reform.roleassignment.controller.advice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorConstantsTest {

    @Test
    void getErrorCode() {
        assertEquals(400, ErrorConstants.BAD_REQUEST.getErrorCode());
    }

    @Test
    void getErrorMessage() {
        assertEquals("Bad Request", ErrorConstants.BAD_REQUEST.getErrorMessage());
    }
}
