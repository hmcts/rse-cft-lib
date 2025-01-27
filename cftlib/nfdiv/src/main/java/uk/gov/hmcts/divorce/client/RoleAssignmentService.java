package uk.gov.hmcts.divorce.client;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.divorce.client.request.MultipleQueryRequest;
import uk.gov.hmcts.divorce.client.response.RoleAssignmentResource;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.sow014.lib.RoleAssignment;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;


@Slf4j
@Service
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class RoleAssignmentService {

    public static final String TOTAL_RECORDS = "Total-Records";
    public static final int DEFAULT_PAGE_NUMBER = 0;
    private final AuthTokenGenerator serviceAuthTokenGenerator;

    private final uk.gov.hmcts.divorce.client.RoleAssignmentServiceApi roleAssignmentServiceApi;

    private final IdamService idamService;
    private final int maxRoleAssignmentRecords;

    @Autowired
    public RoleAssignmentService(RoleAssignmentServiceApi roleAssignmentServiceApi,
                                 AuthTokenGenerator serviceAuthTokenGenerator,
                                 IdamService idamService,
                                 @Value("${role-assignment-service.maxResults}") int maxRoleAssignmentRecords) {
        this.roleAssignmentServiceApi = roleAssignmentServiceApi;
        this.serviceAuthTokenGenerator = serviceAuthTokenGenerator;
        this.idamService = idamService;
        this.maxRoleAssignmentRecords = maxRoleAssignmentRecords;
    }

    public List<RoleAssignment> getRolesForUser(String idamUserId, String authToken) {
        requireNonNull(idamUserId, "IdamUserId cannot be null");

        RoleAssignmentResource roleAssignmentResource = getRoles(idamUserId, authToken);
        return roleAssignmentResource.getRoleAssignmentResponse();
    }

    private RoleAssignmentResource getRoles(String idamUserId, String authToken) {
        try {
            return roleAssignmentServiceApi.getRolesForUser(
                idamUserId,
                authToken,
                serviceAuthTokenGenerator.generate()
            );
        } catch (FeignException ex) {
            log.error("Error when retrieving roles for user '{}'", idamUserId, ex);
            throw new RuntimeException(
                "User did not have sufficient permissions to perform this action", ex);
        }
    }

    public List<RoleAssignment> getRolesByUserId(String userId) {
        requireNonNull(userId, "userId cannot be null");

        RoleAssignmentResource roleAssignmentResponse = roleAssignmentServiceApi.getRolesForUser(
            userId,
            idamService.retrieveSystemUpdateUserDetails().getAuthToken(),
            serviceAuthTokenGenerator.generate()
        );

        return roleAssignmentResponse.getRoleAssignmentResponse();
    }

    public RoleAssignmentResource performSearch(MultipleQueryRequest multipleQueryRequest) {

        int pageNumber = DEFAULT_PAGE_NUMBER;

        try {
            ResponseEntity<RoleAssignmentResource> responseEntity = getPageResponse(
                multipleQueryRequest,
                pageNumber
            );
            List<RoleAssignment> roleAssignments
                = new ArrayList<>(requireNonNull(responseEntity.getBody()).getRoleAssignmentResponse());

            long totalRecords = Long.parseLong(requireNonNull(responseEntity.getHeaders().get(TOTAL_RECORDS)).get(0));
            long totalPageNumber = totalRecords / maxRoleAssignmentRecords;
            while (totalPageNumber > pageNumber) {
                pageNumber += 1;
                responseEntity = getPageResponse(multipleQueryRequest, pageNumber);
                List<RoleAssignment> roleAssignmentResponse = requireNonNull(responseEntity.getBody())
                    .getRoleAssignmentResponse();

                if (!roleAssignmentResponse.isEmpty()) {
                    roleAssignments.addAll(roleAssignmentResponse);
                }
            }
            return new RoleAssignmentResource(roleAssignments);
        } catch (FeignException ex) {
            throw new RuntimeException(
                "Could not retrieve role assignments when performing the search", ex);
        }
    }

    private ResponseEntity<RoleAssignmentResource> getPageResponse(
        MultipleQueryRequest multipleQueryRequest, int pageNumber) {
        return roleAssignmentServiceApi.queryRoleAssignments(
            idamService.retrieveSystemUpdateUserDetails().getAuthToken(),
            serviceAuthTokenGenerator.generate(),
            pageNumber,
            maxRoleAssignmentRecords,
            multipleQueryRequest
        );
    }
}
