
package uk.gov.hmcts.reform.roleassignment.controller.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.roleassignment.auditlog.LogAudit;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.service.createroles.CreateRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.versions.V1;

import java.text.ParseException;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.CREATE_ASSIGNMENTS;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION2;

@RestController
public class CreateAssignmentController {

    private CreateRoleAssignmentOrchestrator createRoleAssignmentOrchestrator;

    public CreateAssignmentController(@Autowired CreateRoleAssignmentOrchestrator createRoleAssignmentOrchestrator) {
        this.createRoleAssignmentOrchestrator = createRoleAssignmentOrchestrator;
    }

    //**************** Create Role Assignment  API ***************

    @PostMapping(
        path = "/am/role-assignments",
        produces = V1.MediaType.CREATE_ASSIGNMENTS,
        consumes = {"application/json"}
    )
    @Operation(summary = "Creates role assignments",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    @ResponseStatus(code = HttpStatus.CREATED)
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        content = @Content(schema = @Schema(implementation = RoleAssignmentRequestResource.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "One of the following reasons:\n"
            + "1. " + V1.Error.INVALID_ROLE_NAME + "\n"
            + "2. " + V1.Error.INVALID_REQUEST + "\n",
        content = @Content()
    )
    @ApiResponse(
        responseCode = "422",
        description = V1.Error.UNPROCESSABLE_ENTITY_REQUEST_REJECTED,
        content = @Content()
    )
    @LogAudit(operationType = CREATE_ASSIGNMENTS,
        process = "#assignmentRequest.request.process",
        reference = "#assignmentRequest.request.reference",
        id = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).buildAssignmentIds(#result)",
        actorId = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).buildActorIds(#result)",
        roleName = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).buildRoleNames(#result)",
        caseId = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).buildCaseIds(#result)",
        assignerId = "#assignmentRequest.request.assignerId",
        correlationId = "#correlationId",
        requestPayload = "#auditContextWith.requestPayload"
    )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<RoleAssignmentRequestResource> createRoleAssignment(
        @RequestHeader(value = "x-correlation-id", required = false)
                                                               String correlationId,
        @Validated
        @RequestBody AssignmentRequest assignmentRequest) throws ParseException {
        return createRoleAssignmentOrchestrator.createRoleAssignment(assignmentRequest);
    }
}
