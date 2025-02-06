package uk.gov.hmcts.reform.roleassignment.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.ActorIdType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.LocalDateTime.now;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@Setter
public class TestDataBuilder {

    public static final String ACTORID = "4772dc44-268f-4d0c-8f83-f0fb662aac84";
    public static final String CASE_ALLOCATOR_ID = "4772dc44-268f-4d0c-8f83-f0fb662aac88";

    private TestDataBuilder() {
        //not meant to be instantiated.
    }

    public static AssignmentRequest buildAssignmentRequest(Status requestStatus, Status roleStatus,
                                                           Boolean replaceExisting) throws IOException {
        return new AssignmentRequest(
            buildRequest(requestStatus, replaceExisting),
            buildRequestedRoleCollection(roleStatus)
        );
    }

    public static AssignmentRequest buildEmptyAssignmentRequest(Status roleStatus) throws IOException {
        return new AssignmentRequest(Request.builder().build(), buildRequestedRoleCollection(roleStatus));
    }

    public static Request buildRequest(Status status, Boolean replaceExisting) {
        return Request.builder()
            .id(UUID.fromString("ab4e8c21-27a0-4abd-aed8-810fdce22adb"))
            .authenticatedUserId(ACTORID)
            .correlationId("38a90097-434e-47ee-8ea1-9ea2a267f51d")
            .assignerId("123e4567-e89b-42d3-a456-556642445678")
            .requestType(RequestType.CREATE)
            .reference("p2")
            .process(("p2"))
            .replaceExisting(replaceExisting)
            .status(status)
            .created(ZonedDateTime.now())
            .clientId("am_role_assignment_service")
            .build();
    }

    public static RoleAssignment buildRoleAssignment_CustomActorId(Status status, String actorId, String path,
                                                                   RoleType roleType, String roleName) {
        ZonedDateTime timeStamp = ZonedDateTime.now();
        return RoleAssignment.builder()
            .id(UUID.fromString("3ed4f960-e50b-4127-af30-47821d5799f7"))
            .actorId(actorId)
            .actorIdType(ActorIdType.IDAM)
            .roleType(roleType)
            .roleName(roleName)
            .classification(Classification.PRIVATE)
            .grantType(GrantType.STANDARD)
            .roleCategory(RoleCategory.LEGAL_OPERATIONS)
            .readOnly(false)
            .beginTime(timeStamp.plusDays(1))
            .created(timeStamp)
            .endTime(timeStamp.plusDays(3))
            .reference("reference")
            .process(("process"))
            .statusSequence(10)
            .status(status)
            .attributes(JacksonUtils.convertValue(buildAttributesFromFile(path)))
            .authorisations(Collections.emptyList())
            .build();
    }

    public static List<RoleAssignment> buildRoleAssignmentList_Custom(Status status, String actorId, String path,
                                                                      RoleType roleType, String roleName) {
        List<RoleAssignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(buildRoleAssignment_CustomActorId(status, actorId, path, roleType, roleName));
        return requestedRoles;
    }

    public static List<Assignment> buildAssignmentList(Status status, String actorId, String path, RoleType roleType,
                                                       String roleName) {
        List<Assignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(buildRoleAssignment_CustomActorId(status, actorId, path, roleType, roleName));
        return requestedRoles;
    }

    public static List<Assignment> buildMultiAssignmentList(Status status, String actorId, String path,
                                                            RoleType roleType, String roleName) {
        List<Assignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(buildRoleAssignment_CustomActorId(status, actorId, path, roleType, roleName));
        requestedRoles.add(buildRoleAssignment_CustomActorId(status, actorId, path, roleType, roleName));
        return requestedRoles;
    }

    public static RoleAssignment buildRoleAssignment(Status status) {
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
        return RoleAssignment.builder()
            .id(UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f"))
            .actorId("21334a2b-79ce-44eb-9168-2d49a744be9c")
            .actorIdType(ActorIdType.IDAM)
            .roleType(RoleType.CASE)
            .roleName("judge")
            .classification(Classification.PUBLIC)
            .grantType(GrantType.STANDARD)
            .roleCategory(RoleCategory.JUDICIAL)
            .readOnly(true)
            .beginTime(timeStamp.plusDays(1))
            .endTime(timeStamp.plusMonths(1))
            .reference("reference")
            .process(("process"))
            .statusSequence(10)
            .status(status)
            .created(ZonedDateTime.now())
            .attributes(JacksonUtils.convertValue(buildAttributesFromFile("attributes.json")))
            .notes(buildNotesFromFile())
            .authorisations(Collections.emptyList())
            .build();
    }

    public static RoleAssignment buildRoleAssignmentUpdated(Status status) {
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
        return RoleAssignment.builder()
            .id(UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f"))
            .actorId("21334a2b-79ce-44eb-9168-2d49a744be9c")
            .actorIdType(ActorIdType.IDAM)
            .roleType(RoleType.CASE)
            .roleName("top dog")
            .classification(Classification.PUBLIC)
            .grantType(GrantType.STANDARD)
            .roleCategory(RoleCategory.JUDICIAL)
            .readOnly(true)
            .beginTime(timeStamp.plusDays(1))
            .endTime(timeStamp.plusMonths(1))
            .reference("new ref")
            .process(("new process"))
            .statusSequence(10)
            .status(status)
            .created(ZonedDateTime.now())
            .attributes(JacksonUtils.convertValue(buildAttributesFromFile("attributes.json")))
            .notes(buildNotesFromFile())
            .build();
    }

    public static ResponseEntity<Object> buildRoleAssignmentResponse(Status requestStatus,
                                                                     Status roleStatus,
                                                                     Boolean replaceExisting) throws Exception {
        return ResponseEntity.status(HttpStatus.OK)
            .body(buildAssignmentRequest(requestStatus, roleStatus, replaceExisting));
    }

    public static ResponseEntity<RoleAssignmentRequestResource> buildAssignmentRequestResource(Status requestStatus,
                                                                                               Status roleStatus,
                                                                                               Boolean replaceExisting)
        throws Exception {
        return ResponseEntity.status(HttpStatus.OK)
            .body(new RoleAssignmentRequestResource(buildAssignmentRequest(
                requestStatus,
                roleStatus,
                replaceExisting
            )));
    }

    public static ResponseEntity<RoleAssignmentResource> buildResourceRoleAssignmentResponse(
        Status roleStatus) throws Exception {
        return ResponseEntity.status(HttpStatus.OK)
            .body(new RoleAssignmentResource(List.of(buildRoleAssignment(roleStatus)), ""));
    }

    public static Collection<RoleAssignment> buildRequestedRoleCollection(Status status) throws IOException {
        Collection<RoleAssignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(buildRoleAssignment(status));
        requestedRoles.add(buildRoleAssignment(status));
        return requestedRoles;
    }

    public static Collection<RoleAssignment> buildRequestedRoleCollection_Updated(Status status) {
        Collection<RoleAssignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(buildRoleAssignmentUpdated(status));
        requestedRoles.add(buildRoleAssignmentUpdated(status));
        return requestedRoles;
    }

    public static JsonNode buildAttributesFromFile(String path) {
        try (InputStream inputStream =
                 TestDataBuilder.class.getClassLoader().getResourceAsStream(path)) {
            assert inputStream != null;
            JsonNode result = new ObjectMapper().readValue(inputStream, new TypeReference<>() {
            });
            inputStream.close();
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode buildNotesFromFile() {
        try (InputStream inputStream =
                 TestDataBuilder.class.getClassLoader().getResourceAsStream("notes.json")) {
            assert inputStream != null;
            JsonNode result = new ObjectMapper().readValue(inputStream, new TypeReference<>() {
            });
            inputStream.close();
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<RoleConfigRole> buildRolesFromFile() {
        return JacksonUtils.getConfiguredRoles();
    }

    public static RequestEntity buildRequestEntity(Request request) {
        return RequestEntity.builder()
            .id(request.getId())
            .assignerId(request.getAssignerId())
            .correlationId(request.getCorrelationId())
            .status(request.getStatus().toString())
            .process(request.getProcess())
            .reference(request.getProcess())
            .authenticatedUserId(request.getAuthenticatedUserId())
            .clientId(request.getClientId())
            .assignerId(request.getAssignerId())
            .replaceExisting(request.isReplaceExisting())
            .requestType(request.getRequestType().toString())
            .created(request.getCreated().toLocalDateTime())
            .log(request.getLog())
            .build();
    }

    public static HistoryEntity buildHistoryIntoEntity(RoleAssignment model, RequestEntity requestEntity) {
        return HistoryEntity.builder().id(model.getId()).actorId(model.getActorId())
            .actorIdType(model.getActorIdType().toString())
            .attributes(JacksonUtils.convertValueJsonNode(model.getAttributes()))
            .beginTime(model.getBeginTime().toLocalDateTime())
            .classification(model.getClassification().toString())
            .endTime(model.getEndTime().toLocalDateTime())
            .grantType(model.getGrantType().toString())
            .roleName(model.getRoleName())
            .roleType(model.getRoleType().toString())
            .readOnly(model.isReadOnly())
            .status(model.getStatus().toString())
            .requestEntity(requestEntity)
            .process(model.getProcess())
            .reference(model.getReference())
            .created(model.getCreated().toLocalDateTime())
            .notes(model.getNotes())
            .build();
    }

    public static RoleAssignmentEntity convertRoleAssignmentToEntity(RoleAssignment model) {
        return RoleAssignmentEntity.builder()
            .id(model.getId())
            .actorId(model.getActorId())
            .actorIdType(model.getActorIdType().toString())
            .attributes(JacksonUtils.convertValueJsonNode(model.getAttributes()))
            .beginTime(model.getBeginTime().toLocalDateTime())
            .classification(model.getClassification().toString())
            .endTime(model.getEndTime().toLocalDateTime())
            .created(model.getCreated().toLocalDateTime())
            .grantType(model.getGrantType().toString())
            .roleName(model.getRoleName())
            .roleType(model.getRoleType().toString())
            .readOnly(model.isReadOnly())
            .build();
    }

    public static RoleAssignment convertHistoryEntityInModel(HistoryEntity historyEntity) {

        RoleAssignment requestedrole = new RoleAssignment();
        requestedrole.setId(historyEntity.getId());
        requestedrole.setActorId(historyEntity.getActorId());
        requestedrole.setActorIdType(ActorIdType.valueOf(historyEntity.getActorIdType()));
        requestedrole.setAttributes(JacksonUtils.convertValue(historyEntity.getAttributes()));
        requestedrole.setBeginTime(historyEntity.getBeginTime().atZone(ZoneId.of("UTC")));
        requestedrole.setEndTime(historyEntity.getEndTime().atZone(ZoneId.of("UTC")));
        requestedrole.setCreated(historyEntity.getCreated().atZone(ZoneId.of("UTC")));
        requestedrole.setClassification(Classification.valueOf(historyEntity.getClassification()));
        requestedrole.setGrantType(GrantType.valueOf(historyEntity.getGrantType()));
        requestedrole.setReadOnly(historyEntity.isReadOnly());
        requestedrole.setRoleName(historyEntity.getRoleName());
        requestedrole.setRoleType(RoleType.valueOf(historyEntity.getRoleType()));
        requestedrole.setStatus(Status.valueOf(historyEntity.getStatus()));
        return requestedrole;

    }

    public static HistoryEntity buildHistoryEntity(RoleAssignment roleAssignment, RequestEntity requestEntity) {
        String[] auths = {"dev", "test"};
        return HistoryEntity.builder()
            .actorId(roleAssignment.getActorId())
            .actorIdType(roleAssignment.getActorIdType().toString())
            .classification(roleAssignment.getClassification().toString())
            .grantType(roleAssignment.getGrantType().toString())
            .roleName(roleAssignment.getRoleName())
            .roleType(roleAssignment.getRoleType().toString())
            .roleCategory(roleAssignment.getRoleCategory().toString())
            .readOnly(roleAssignment.isReadOnly())
            .status(roleAssignment.getStatus().toString())
            .requestEntity(requestEntity)
            .process(roleAssignment.getProcess())
            .reference(roleAssignment.getReference())
            .created(roleAssignment.getCreated().toLocalDateTime())
            .beginTime(roleAssignment.getBeginTime().toLocalDateTime())
            .endTime(roleAssignment.getEndTime().toLocalDateTime())
            .attributes(JacksonUtils.convertValueJsonNode(roleAssignment.getAttributes()))
            .notes(roleAssignment.getNotes())
            .sequence(roleAssignment.getStatusSequence())
            .log(roleAssignment.getLog())
            .authorisations(auths)
            .build();
    }

    public static RoleAssignmentEntity buildRoleAssignmentEntity(RoleAssignment roleAssignment) {
        return RoleAssignmentEntity.builder()
            .id(roleAssignment.getId())
            .actorId(roleAssignment.getActorId())
            .actorIdType(roleAssignment.getActorIdType().toString())
            .attributes(JacksonUtils.convertValueJsonNode(roleAssignment.getAttributes()))
            .beginTime(roleAssignment.getBeginTime().toLocalDateTime())
            .classification(roleAssignment.getClassification().toString())
            .endTime(roleAssignment.getEndTime().toLocalDateTime())
            .created(roleAssignment.getCreated().toLocalDateTime())
            .grantType(roleAssignment.getGrantType().toString())
            .roleName(roleAssignment.getRoleName())
            .roleType(roleAssignment.getRoleType().toString())
            .readOnly(roleAssignment.isReadOnly())
            .roleCategory(roleAssignment.getRoleCategory().toString())
            .authorisations(roleAssignment.getAuthorisations().toArray(new String[0]))
            .build();
    }

    public static UserInfo buildUserInfo(String uuid) {
        List<String> list = new ArrayList<>();
        List<RoleConfigRole> roles = TestDataBuilder.buildRolesFromFile();
        for (RoleConfigRole role : roles) {
            list.add(role.toString());
        }
        return UserInfo.builder().sub("sub").uid(uuid)
            .name("James").givenName("007").familyName("Bond").roles(list).build();
    }

    public static Case buildCase() {
        return Case.builder()

            .id("1234").build();
    }

    public static AssignmentRequest createRoleAssignmentRequest(
        boolean replaceExisting, boolean readOnly) {
        return new AssignmentRequest(
            buildRequestForRoleAssignment(replaceExisting),
            buildRequestedRoles(readOnly)
        );
    }

    public static Request buildRequestForRoleAssignment(boolean replaceExisting) {
        return Request.builder()
            .assignerId("123e4567-e89b-42d3-a456-556642445678")
            .reference("S-052")
            .process(("S-052"))
            .replaceExisting(replaceExisting)
            .build();
    }

    public static Collection<RoleAssignment> buildRequestedRoles(boolean readOnly) {
        Collection<RoleAssignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(buildRoleAssignments(readOnly));
        return requestedRoles;
    }

    public static RoleAssignment buildRoleAssignments(boolean readOnly) {
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
        return RoleAssignment.builder()
            .actorId("123e4567-e89b-42d3-a456-556642445612")
            .actorIdType(ActorIdType.IDAM)
            .roleType(RoleType.CASE)
            .roleName("lead-judge")
            .classification(Classification.PUBLIC)
            .grantType(GrantType.SPECIFIC)
            .roleCategory(RoleCategory.JUDICIAL)
            .readOnly(readOnly)
            .beginTime(timeStamp.plusDays(1))
            .endTime(timeStamp.plusMonths(1))
            .attributes(JacksonUtils.convertValue(buildAttributesFromFile("attributes.json")))
            .notes(buildNotesFromFile())
            .authorisations(Collections.emptyList())
            .build();
    }

    public static RoleAssignment buildRoleAssignmentForConflict(RoleCategory roleCategory) {
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
        return RoleAssignment.builder()
            .actorId(ACTORID)
            .actorIdType(ActorIdType.IDAM)
            .roleType(RoleType.CASE)
            .roleName("conflict-of-interest")
            .status(CREATE_REQUESTED)
            .classification(Classification.RESTRICTED)
            .grantType(GrantType.EXCLUDED)
            .roleCategory(roleCategory)
            .readOnly(false)
            .beginTime(timeStamp.plusDays(1))
            .endTime(timeStamp.plusMonths(1))
            .notes(buildNotesFromFile())
            .attributes(JacksonUtils.convertValue(buildAttributesFromFile("attributes.json")))
            .authorisations(Collections.emptyList())
            .build();
    }

    public static QueryRequest createQueryRequest() {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = List.of("London", "JAPAN");
        List<String> contractTypes = List.of("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);

        return QueryRequest.builder()
            .actorId(List.of("123e4567-e89b-42d3-a456-556642445612"))

            .roleType(List.of(RoleType.CASE.toString()))
            .roleName(List.of("judge"))
            .classification(List.of(Classification.PUBLIC.toString()))
            .grantType(List.of(GrantType.SPECIFIC.toString()))
            .roleCategory(List.of(RoleCategory.JUDICIAL.toString()))
            .validAt(now())
            .attributes(attributes)
            .authorisations(List.of("dev"))
            .build();


    }

    public static ExistingRoleAssignment buildExistingRole(String actorId, String roleName,
                                                           RoleCategory roleCategory,
                                                           Map<String, JsonNode> attributes,
                                                           RoleType roleType,
                                                           Classification classification,
                                                           GrantType grantType,
                                                           Status status) {
        return ExistingRoleAssignment.builder()
            .actorId(actorId)
            .roleType(roleType)
            .roleCategory(roleCategory)
            .roleName(roleName)
            .classification(classification)
            .grantType(grantType)
            .attributes(attributes)
            .status(status)
            .build();
    }

    public static ExistingRoleAssignment buildExistingRoleForConflict(String juris, RoleCategory roleCategory) {
        Map<String, JsonNode> attributes = new HashMap<>();
        attributes.put("jurisdiction", convertValueJsonNode(juris));
        return ExistingRoleAssignment.builder()
            .actorId(ACTORID)
            .roleType(RoleType.ORGANISATION)
            .roleCategory(roleCategory)
            .grantType(GrantType.STANDARD)
            .roleName("case-allocator")
            .classification(Classification.PUBLIC)
            .attributes(attributes)
            .build();

    }

    public static ExistingRoleAssignment buildExistingRoleForDrools(String actorId, String roleName,
                                                                    RoleCategory roleCategory,
                                                                    Map<String, JsonNode> attributes,
                                                                    Classification classification,
                                                                    GrantType grantType,
                                                                    RoleType roleType) {
        return ExistingRoleAssignment.builder()
            .actorId(actorId)
            .roleType(roleType)
            .roleCategory(roleCategory)
            .roleName(roleName)
            .classification(classification)
            .grantType(grantType)
            .attributes(attributes)
            .beginTime(ZonedDateTime.now().minusDays(1L))
            .endTime(ZonedDateTime.now().plusHours(1L))
            .authorisations(List.of("CCD", "ExUI", "SSIC", "RefData"))
            .build();

    }

    public static List<RoleAssignment> getRequestedOrgRole() {
        return List.of(RoleAssignment.builder()
                                 .id(UUID.fromString("9785c98c-78f2-418b-ab74-a892c3ccca9f"))
                                 .actorId("4772dc44-268f-4d0c-8f83-f0fb662aac83")
                                 .actorIdType(ActorIdType.IDAM)
                                 .classification(Classification.PUBLIC)
                                 .readOnly(true)
                                 .status(CREATE_REQUESTED)
                                 .attributes(new HashMap<>())
                                 .build());
    }

    public static AssignmentRequest.AssignmentRequestBuilder getAssignmentRequest() {
        return AssignmentRequest.builder().request(Request.builder()
                                                       .id(UUID.fromString("ab4e8c21-27a0-4abd-aed8-810fdce22adb"))
                                                       .authenticatedUserId(ACTORID)
                                                       .correlationId("38a90097-434e-47ee-8ea1-9ea2a267f51d")
                                                       .assignerId(ACTORID)
                                                       .requestType(RequestType.CREATE)
                                                       .reference(ACTORID)
                                                       .process(("p2"))
                                                       .replaceExisting(true)
                                                       .status(Status.CREATED)
                                                       .created(ZonedDateTime.now())
                                                       .build());


    }

    public static RoleAssignment getRequestedCaseRole(RoleCategory roleCategory, String roleName, GrantType grantType) {
        return RoleAssignment.builder()
            .id(UUID.randomUUID())
            .actorId(UUID.randomUUID().toString())
            .actorIdType(ActorIdType.IDAM)
            .roleCategory(roleCategory)
            .roleType(RoleType.CASE)
            .roleName(roleName)
            .grantType(grantType)
            .classification(Classification.PUBLIC)
            .readOnly(true)
            .status(CREATE_REQUESTED)
            .attributes(new HashMap<>())
            .build();
    }

    public static RoleAssignment getRequestedCaseRole_ra(RoleCategory roleCategory, String roleName,
                                                         GrantType grantType,
                                                         String attributeKey,
                                                         String attributeVal,
                                                         Status status) {
        RoleAssignment ra = RoleAssignment.builder()
            .id(UUID.randomUUID())
            .actorId(UUID.randomUUID().toString())
            .actorIdType(ActorIdType.IDAM)
            .roleCategory(roleCategory)
            .roleType(RoleType.CASE)
            .roleName(roleName)
            .grantType(grantType)
            .classification(Classification.PUBLIC)
            .readOnly(true)
            .status(status)
            .attributes(new HashMap<>())
            .build();
        ra.setAttribute(attributeKey, attributeVal);
        ra.setAttribute("jurisdiction","IA");
        return ra;
    }

    public static RoleAssignment getRequestedCaseRole_Ia(RoleCategory roleCategory, String roleName,
                                                         GrantType grantType,
                                                         Map<String,JsonNode> listOfAttributes,
                                                         Status status) {
        RoleAssignment ra = RoleAssignment.builder()
            .id(UUID.randomUUID())
            .actorId(UUID.randomUUID().toString())
            .actorIdType(ActorIdType.IDAM)
            .roleCategory(roleCategory)
            .roleType(RoleType.CASE)
            .roleName(roleName)
            .grantType(grantType)
            .classification(Classification.PUBLIC)
            .readOnly(true)
            .status(status)
            .attributes(new HashMap<>())
            .build();
        ra.setAttributes(listOfAttributes);
        ra.setAttribute("jurisdiction","IA");
        return ra;
    }

    public static RoleAssignment getRequestedOrgRole_ra(RoleCategory roleCategory, String roleName,
                                                        GrantType grantType, String attributeKey,
                                                        String attributeVal, Status status,
                                                        Classification classification,
                                                        Boolean readOnly) {
        RoleAssignment ra = RoleAssignment.builder()
            .id(UUID.randomUUID())
            .actorId(UUID.randomUUID().toString())
            .actorIdType(ActorIdType.IDAM)
            .roleCategory(roleCategory)
            .roleType(RoleType.ORGANISATION)
            .roleName(roleName)
            .grantType(grantType)
            .classification(classification)
            .readOnly(readOnly)
            .status(status)
            .attributes(new HashMap<>())
            .build();
        ra.setAttribute(attributeKey, attributeVal);
        return ra;
    }

    public static AssignmentRequest.AssignmentRequestBuilder buildAssignmentRequestForSpecialAccess(
        String process,
        String roleName,
        RoleCategory roleCategory,
        RoleType roleType,
        HashMap<String, JsonNode> attributes,
        Classification classification,
        GrantType grantType,
        Status status,
        String clientId,
        boolean readOnly,
        String notes, String actorId,
        String requestedActorId,
        String reference) {

        return AssignmentRequest.builder()
            .request(Request.builder()
                         .id(UUID.fromString("ab4e8c21-27a0-4abd-aed8-810fdce22adb"))
                         .authenticatedUserId(actorId)
                         .clientId(clientId)
                         .correlationId("38a90097-434e-47ee-8ea1-9ea2a267f51d")
                         .assignerId(actorId)
                         .requestType(RequestType.CREATE)
                         .reference(reference)
                         .process(process)
                         .replaceExisting(true)
                         .created(ZonedDateTime.now())
                         .build())
            .requestedRoles(List.of(
                RoleAssignment.builder()
                    .actorId(requestedActorId)
                    .status(status)
                    .roleType(roleType)
                    .roleName(roleName)
                    .roleCategory(roleCategory)
                    .grantType(grantType)
                    .readOnly(readOnly)
                    .classification(classification)
                    .endTime(ZonedDateTime.now().plusHours(1L))
                    .notes(convertValueJsonNode(List.of(notes)))
                    .attributes(attributes)
                    .build()
            ));
    }

    public static AssignmentRequest.AssignmentRequestBuilder buildAssignmentRequestSpecialAccessGrant(
        String process, String roleName, RoleCategory roleCategory, RoleType roleType,
        HashMap<String, JsonNode> attributes, Classification classification, GrantType grantType, Status status,
        String clientId, boolean readOnly,String notes, String requestedActorId, String reference) {

        return buildAssignmentRequestForSpecialAccess(process, roleName, roleCategory, roleType, attributes,
                                                      classification, grantType, status, clientId, readOnly,
                                                      notes, CASE_ALLOCATOR_ID, requestedActorId, reference);
    }

    public static AssignmentRequest.AssignmentRequestBuilder buildAssignmentRequestSpecialAccess(
        String process, String roleName, RoleCategory roleCategory, RoleType roleType,
        HashMap<String, JsonNode> attributes, Classification classification, GrantType grantType,
        Status status, String clientId, boolean readOnly, String notes, String requestedActorId,
        String reference) {

        return buildAssignmentRequestForSpecialAccess(process, roleName, roleCategory, roleType, attributes,
                                                      classification, grantType, status, clientId, readOnly,
                                                      notes, ACTORID, requestedActorId, reference);
    }


}
