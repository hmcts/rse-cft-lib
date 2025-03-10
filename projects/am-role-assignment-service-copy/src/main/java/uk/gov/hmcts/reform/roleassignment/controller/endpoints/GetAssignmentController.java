
package uk.gov.hmcts.reform.roleassignment.controller.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.roleassignment.auditlog.LogAudit;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.service.getroles.RetrieveRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.versions.V1;

import java.util.List;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.GET_ASSIGNMENTS_BY_ACTOR;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION2;

@Slf4j
@RestController
public class GetAssignmentController {

    private RetrieveRoleAssignmentOrchestrator retrieveRoleAssignmentService;

    public GetAssignmentController(@Autowired RetrieveRoleAssignmentOrchestrator retrieveRoleAssignmentService) {
        this.retrieveRoleAssignmentService = retrieveRoleAssignmentService;
    }
    //**************** Get role assignment records by actorId API ***************

    @GetMapping(
        path = "/am/role-assignments/actors/{actorId}",
        produces = V1.MediaType.GET_ASSIGNMENTS
    )
    @Operation(summary = "Get role assignments by actor Id",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    @ApiResponse(
        responseCode = "200",
        description = "Success",
        content = @Content(schema = @Schema(implementation = RoleAssignmentResource.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = V1.Error.INVALID_REQUEST,
        content = @Content()
    )
    @LogAudit(operationType = GET_ASSIGNMENTS_BY_ACTOR,
        size = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).sizeOfAssignments(#result)",
        actorId = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).getActorIds(#result)",
        correlationId = "#correlationId",
        requestPayload = "#auditContextWith.requestPayload"
    )
    public ResponseEntity<RoleAssignmentResource> retrieveRoleAssignmentsByActorId(
                                 @RequestHeader(value = "x-correlation-id",
                                 required = false) String correlationId,
                                @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
                                 @Parameter(required = true)
                       @PathVariable("actorId") String actorId) {

        ResponseEntity<RoleAssignmentResource> responseEntity = retrieveRoleAssignmentService.getAssignmentsByActor(
            actorId
        );
        return ResponseEntity.status(HttpStatus.OK).body(responseEntity.getBody());
    }

    //**************** Get role configurations API ***************

    @GetMapping(
        path = "/am/role-assignments/roles",
        produces = V1.MediaType.GET_ROLES
    )
    @Operation(summary = "Get roles",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    @ResponseStatus(code = HttpStatus.OK)
    @ApiResponse(
        responseCode = "200",
        description = "Ok",
        content = @Content(schema = @Schema(implementation = Object.class))
    )
    public ResponseEntity<List<RoleConfigRole>> getListOfRoles(@RequestHeader(value = "x-correlation-id",
        required = false) String correlationId)  {
        List<RoleConfigRole> rootNode = retrieveRoleAssignmentService.getListOfRoles();
        return ResponseEntity.status(HttpStatus.OK).body(rootNode);
    }
}
