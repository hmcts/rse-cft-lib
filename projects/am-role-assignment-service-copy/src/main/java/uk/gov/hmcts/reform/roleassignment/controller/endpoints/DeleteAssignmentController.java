
package uk.gov.hmcts.reform.roleassignment.controller.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.roleassignment.auditlog.LogAudit;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.service.deleteroles.DeleteRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.versions.V1;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.DELETE_ASSIGNMENTS_BY_ID;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.DELETE_ASSIGNMENTS_BY_PROCESS;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.DELETE_ASSIGNMENTS_BY_QUERY;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION2;


@RestController
public class DeleteAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(DeleteAssignmentController.class);

    private DeleteRoleAssignmentOrchestrator deleteRoleAssignmentOrchestrator;


    public DeleteAssignmentController(@Autowired DeleteRoleAssignmentOrchestrator deleteRoleAssignmentOrchestrator
    ) {
        this.deleteRoleAssignmentOrchestrator = deleteRoleAssignmentOrchestrator;

    }

    @DeleteMapping(
        path = "am/role-assignments",
        produces = V1.MediaType.DELETE_ASSIGNMENTS
    )
    @Operation(summary = "Delete role assignments",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    @ApiResponse(
        responseCode = "204",
        description = "No Content",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "400",
        description = "One of the following reasons:\n"
            + "1. " + V1.Error.BAD_REQUEST_INVALID_PARAMETER + "\n"
            + "2. " + V1.Error.BAD_REQUEST_MISSING_PARAMETERS + "\n",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "422",
        description = V1.Error.UNPROCESSABLE_ENTITY_REQUEST_REJECTED,
        content = @Content()
    )
    @LogAudit(operationType = DELETE_ASSIGNMENTS_BY_PROCESS,
        process = "#process",
        reference = "#reference",
        correlationId = "#correlationId",
        requestPayload = "#auditContextWith.requestPayload"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<Void> deleteRoleAssignment(@RequestHeader(value = "x-correlation-id",
        required = false)
                                                         String correlationId,
                                                     @RequestParam(value = "process", required = false)
                                                         String process,
                                                     @RequestParam(value = "reference", required = false)
                                                         String reference) {
        return deleteRoleAssignmentOrchestrator.deleteRoleAssignmentByProcessAndReference(process, reference);
    }

    @DeleteMapping(
        path = "am/role-assignments/{assignmentId}",
        produces = V1.MediaType.DELETE_ASSIGNMENTS
    )
    @Operation(summary = "Delete role assignments by assignment Id",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    @ApiResponse(
        responseCode = "204",
        description = "No Content",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "400",
        description = "One of the following reasons:\n"
            + "1. " + V1.Error.BAD_REQUEST_INVALID_PARAMETER + "\n"
            + "2. " + V1.Error.BAD_REQUEST_MISSING_PARAMETERS + "\n",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "422",
        description = V1.Error.UNPROCESSABLE_ENTITY_REQUEST_REJECTED,
        content = @Content()
    )
    @LogAudit(operationType = DELETE_ASSIGNMENTS_BY_ID,
        assignmentId = "#assignmentId",
        correlationId = "#correlationId",
        requestPayload = "#auditContextWith.requestPayload"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<Void> deleteRoleAssignmentById(@RequestHeader(
        value = "x-correlation-id",
        required = false)
                                                             String correlationId,
                                                         @Parameter(required = true)
                                                         @PathVariable String assignmentId) {
        return deleteRoleAssignmentOrchestrator.deleteRoleAssignmentByAssignmentId(assignmentId);
    }

    @PostMapping(
        path = "/am/role-assignments/query/delete",
        consumes = "application/json",
        produces = V1.MediaType.POST_DELETE_ASSIGNMENTS_BY_QUERY_REQUEST
    )
    @Operation(summary = "Delete role assignments by query",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    @ResponseStatus(code = HttpStatus.OK)
    @ApiResponse(
        responseCode = "200",
        description = "The assignment records have been deleted.",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "400",
        description = "One of the following reasons:\n"
            + "1. " + V1.Error.BAD_REQUEST_INVALID_PARAMETER + "\n"
            + "2. " + V1.Error.BAD_REQUEST_MISSING_PARAMETERS + "\n",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "422",
        description = V1.Error.UNPROCESSABLE_ENTITY_REQUEST_REJECTED,
        content = @Content()
    )
    @LogAudit(operationType = DELETE_ASSIGNMENTS_BY_QUERY,
        requestPayload = "#auditContextWith.requestPayload",
        correlationId = "#correlationId"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<Void> deleteRoleAssignmentsByQuery(@RequestHeader(
        value = "x-correlation-id",
        required = false)
                                                                 String correlationId,
                                                             @Parameter(required = true)
                                                             @Validated @RequestBody(required = true)
                                                                 MultipleQueryRequest multipleQueryRequest) {
        logger.info("Inside the Delete role assignment records by multiple query request method");
        return deleteRoleAssignmentOrchestrator.deleteRoleAssignmentsByQuery(multipleQueryRequest);
    }
}
