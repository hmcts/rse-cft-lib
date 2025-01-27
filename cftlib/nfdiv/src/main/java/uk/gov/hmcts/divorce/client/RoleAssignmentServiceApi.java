package uk.gov.hmcts.divorce.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.hmcts.divorce.client.request.MultipleQueryRequest;
import uk.gov.hmcts.divorce.client.response.RoleAssignmentResource;

@FeignClient(
        name = "role-assignment-api",
        url = "${role-assignment-service.url}",
        configuration = FeignConfiguration.class
)
@SuppressWarnings("checkstyle:LineLength")
public interface RoleAssignmentServiceApi {

    public final String AUTHORIZATION = "Authorization";
    public final String SERVICE_AUTHORIZATION = "ServiceAuthorization";

    String V2_MEDIA_TYPE_POST_ASSIGNMENTS =
            "application/vnd.uk.gov.hmcts.role-assignment-service"
                    + ".post-assignment-query-request+json;charset=UTF-8;version=2.0";

    @GetMapping(
            value = "/am/role-assignments/actors/{user-id}",
            produces = "application/vnd.uk.gov.hmcts.role-assignment-service.get-assignments+json;charset=UTF-8;version=1.0"
    )
    RoleAssignmentResource getRolesForUser(@PathVariable("user-id") String userId,
                                           @RequestHeader(AUTHORIZATION) String userToken,
                                           @RequestHeader(SERVICE_AUTHORIZATION) String serviceAuthToken);


    @DeleteMapping(
            value = "/am/role-assignments/{role-assignment-id}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void deleteRoleAssignmentById(@PathVariable("role-assignment-id") String roleAssignmentId,
                                  @RequestHeader(AUTHORIZATION) String userToken,
                                  @RequestHeader(SERVICE_AUTHORIZATION) String serviceAuthToken);

    @PostMapping(
            value = "/am/role-assignments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void createRoleAssignment(@RequestBody String body,
                              @RequestHeader(AUTHORIZATION) String userToken,
                              @RequestHeader(SERVICE_AUTHORIZATION) String serviceAuthToken);

    @PostMapping(
            value = "/am/role-assignments/query",
            consumes = V2_MEDIA_TYPE_POST_ASSIGNMENTS,
            produces = V2_MEDIA_TYPE_POST_ASSIGNMENTS)
    ResponseEntity<RoleAssignmentResource> queryRoleAssignments(
            @RequestHeader(AUTHORIZATION) String userToken,
            @RequestHeader(SERVICE_AUTHORIZATION) String s2sToken,
            @RequestHeader("pageNumber") Integer pageNumber,
            @RequestHeader("size") Integer size,
            @RequestBody MultipleQueryRequest queryRequest
    );
}
