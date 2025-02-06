package uk.gov.hmcts.reform.roleassignment.controller.advice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
public class UnprocessableEntityException extends RuntimeException {

    private static final long serialVersionUID = 7L;

    public UnprocessableEntityException(String  message) {
        super(message);

    }
}
