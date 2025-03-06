package uk.gov.hmcts.reform.roleassignment.controller.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.roleassignment.auditlog.LogAudit;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.service.queryroles.QueryRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.versions.V1;
import uk.gov.hmcts.reform.roleassignment.versions.V2;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.roleassignment.auditlog.AuditOperationType.SEARCH_ASSIGNMENTS;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION2;

@RestController
public class QueryAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(QueryAssignmentController.class);
    private final QueryRoleAssignmentOrchestrator queryRoleAssignmentOrchestrator;

    public QueryAssignmentController(QueryRoleAssignmentOrchestrator queryRoleAssignmentOrchestrator) {
        this.queryRoleAssignmentOrchestrator = queryRoleAssignmentOrchestrator;
    }


    @GetMapping(path = "/am/role-assignments/ld/endpoint")
    public ResponseEntity<Object> getIdLdDemo(@RequestHeader(value = "x-correlation-id",
        required = false) String correlationId) {
        return ResponseEntity.status(HttpStatus.OK).body("Launch Darkly flag check is successful for the endpoint");
    }

    @PostMapping(
        path = "/am/role-assignments/query",
        produces = V1.MediaType.POST_ASSIGNMENTS
    )
    @Operation(summary = "Query role assignments",
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
        description = V1.Error.BAD_REQUEST_INVALID_PARAMETER,
        content = @Content()
    )

    @LogAudit(operationType = SEARCH_ASSIGNMENTS,
        size = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).sizeOfAssignments(#result)",
        correlationId = "#corsrelationId",
        requestPayload = "#auditContextWith.requestPayload"
    )
    public ResponseEntity<RoleAssignmentResource> retrieveRoleAssignmentsByQueryRequest(
        @RequestHeader(value = "x-correlation-id",
            required = false) String correlationId,
        @RequestHeader(value = "pageNumber", required = false) Integer pageNumber,
        @RequestHeader(value = "size", required = false) Integer size,
        @RequestHeader(value = "sort", required = false) String sort,
        @RequestHeader(value = "direction", required = false) String direction,
        @Validated @RequestBody(required = true) QueryRequest queryRequest,
        @RequestParam(value = "includeLabels", defaultValue = "false") Boolean includeLabels) {
        logger.info("Inside Single query request method");
        return queryRoleAssignmentOrchestrator
            .retrieveRoleAssignmentsByQueryRequest(queryRequest, pageNumber, size, sort, direction, includeLabels);
    }

    @PostMapping(
        path = "/am/role-assignments/query",
        consumes = V2.MediaType.POST_ASSIGNMENTS,
        produces = V2.MediaType.POST_ASSIGNMENTS
    )
    @Operation(summary = "Query role assignments v2",
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
        description = V1.Error.BAD_REQUEST_INVALID_PARAMETER,
        content = @Content()
    )
    @LogAudit(operationType = SEARCH_ASSIGNMENTS,
        size = "T(uk.gov.hmcts.reform.roleassignment.util.AuditLoggerUtil).sizeOfAssignments(#result)",
        correlationId = "#corsrelationId",
        requestPayload = "#auditContextWith.requestPayload")
    public ResponseEntity<RoleAssignmentResource> retrieveRoleAssignmentsByQueryRequestV2(
        @RequestHeader(value = "x-correlation-id",
            required = false) String correlationId,
        @RequestHeader(value = "pageNumber", required = false) Integer pageNumber,
        @RequestHeader(value = "size", required = false) Integer size,
        @RequestHeader(value = "sort", required = false) String sort,
        @RequestHeader(value = "direction", required = false) String direction,
        @Validated @RequestBody(required = true) MultipleQueryRequest multipleQueryRequest,
        @RequestParam(value = "includeLabels", defaultValue = "false") Boolean includeLabels) {

        if (CollectionUtils.isEmpty(multipleQueryRequest.getQueryRequests())) {
            throw new BadRequestException("Request Payload is invalid");
        }
        logger.info("Inside Multiple query request method");
        return queryRoleAssignmentOrchestrator
            .retrieveRoleAssignmentsByMultipleQueryRequest(multipleQueryRequest, pageNumber, size, sort, direction,
                                                           includeLabels);
    }
}
