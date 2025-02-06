package uk.gov.hmcts.divorce.client;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.divorce.client.request.*;
import uk.gov.hmcts.divorce.client.response.RoleAssignmentResource;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;
import uk.gov.hmcts.divorce.solicitor.service.RoleAssignmentCategoryService;
import uk.gov.hmcts.divorce.sow014.lib.GrantType;
import uk.gov.hmcts.divorce.sow014.lib.RoleAssignment;
import uk.gov.hmcts.divorce.sow014.lib.RoleAssignmentAttributes;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.document.am.model.Classification;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.APPLICANT_2;


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
    private final RoleAssignmentCategoryService roleAssignmentCategoryService;

    @Autowired
    public RoleAssignmentService(RoleAssignmentServiceApi roleAssignmentServiceApi,
                                 AuthTokenGenerator serviceAuthTokenGenerator,
                                 IdamService idamService,
                                 @Value("${role-assignment-service.maxResults}") int maxRoleAssignmentRecords,
                                 RoleAssignmentCategoryService roleAssignmentCategoryService) {
        this.roleAssignmentServiceApi = roleAssignmentServiceApi;
        this.serviceAuthTokenGenerator = serviceAuthTokenGenerator;
        this.idamService = idamService;
        this.maxRoleAssignmentRecords = maxRoleAssignmentRecords;
        this.roleAssignmentCategoryService = roleAssignmentCategoryService;
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

    public RoleAssignmentRequestResponse createRoleAssignment(CaseDetails<CaseData, State> caseDetails, User caseworkerUser,
                                                              String applicant2UserId) {
        UserInfo userDetails = caseworkerUser.getUserDetails();
        var roleCategory = roleAssignmentCategoryService.getRoleCategory(userDetails.getRoles());

        String userId = userDetails.getUid();
        RoleRequest roleRequest = RoleRequest.builder()
            .assignerId(userId)
            .process("CCD")
            .reference(createRoleRequestReference(caseDetails, userId))
            .replaceExisting(false)
            .build();

        List<RoleAssignment> requestedRoles = List.of(RoleAssignment.builder()
            .actorIdType(ActorIdType.IDAM.name())
            .actorId(applicant2UserId)
            .roleType(RoleType.CASE.name())
            .roleName(APPLICANT_2.getRole())
            .classification(Classification.RESTRICTED.name())
            .grantType(GrantType.SPECIFIC.name())
            .roleCategory(roleCategory.name())
            .readOnly(false)
            .beginTime(Instant.now())
            .attributes(RoleAssignmentAttributes.builder()
                .jurisdiction(Optional.of(caseDetails.getJurisdiction()))
                .caseType(Optional.of(caseDetails.getCaseTypeId()))
                .caseId(Optional.of(caseDetails.getId().toString()))
                .build())
            .build());

        RoleAssignmentRequest assignmentRequest = RoleAssignmentRequest.builder()
            .roleRequest(roleRequest)
            .requestedRoles(requestedRoles)
            .build();

        return roleAssignmentServiceApi.createRoleAssignment(
            assignmentRequest,
            caseworkerUser.getAuthToken(),
            serviceAuthTokenGenerator.generate()
        );
    }

    private String createRoleRequestReference(CaseDetails<CaseData, State> caseDetails, String userId) {
        return caseDetails.getId() + "-" + userId;
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
