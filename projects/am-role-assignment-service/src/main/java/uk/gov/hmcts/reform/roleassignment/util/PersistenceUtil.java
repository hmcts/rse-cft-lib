package uk.gov.hmcts.reform.roleassignment.util;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.data.FlagConfig;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.FlagRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.ActorIdType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Service
public class PersistenceUtil {

    public HistoryEntity convertRoleAssignmentToHistoryEntity(RoleAssignment roleAssignment,
                                                              RequestEntity requestEntity) {
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
            .beginTime(roleAssignment.getBeginTime() != null ? roleAssignment.getBeginTime().toLocalDateTime() : null)
            .endTime(roleAssignment.getEndTime() != null ? roleAssignment.getEndTime().toLocalDateTime() : null)
            .attributes(JacksonUtils.convertValueJsonNode(roleAssignment.getAttributes()))
            .notes(roleAssignment.getNotes())
            .log(roleAssignment.getLog())
            .authorisations(!CollectionUtils.isEmpty(roleAssignment.getAuthorisations())
                                ? roleAssignment.getAuthorisations().toArray(new String[0]) : null)
            .build();
    }

    public RequestEntity convertRequestToEntity(Request request) {
        return RequestEntity.builder()
            .correlationId(request.getCorrelationId())
            .status(request.getStatus().toString())
            .process(request.getProcess())
            .reference(request.getReference())
            .authenticatedUserId(request.getAuthenticatedUserId())
            .clientId(request.getClientId())
            .assignerId(request.getAssignerId())
            .replaceExisting(request.isReplaceExisting())
            .requestType(request.getRequestType().toString())
            .created(request.getCreated().toLocalDateTime())
            .log(request.getLog())
            .roleAssignmentId(request.getRoleAssignmentId())
            .build();

    }

    public RoleAssignmentEntity convertRoleAssignmentToEntity(RoleAssignment roleAssignment, boolean isNewFlag) {
        return RoleAssignmentEntity.builder()
            .id(roleAssignment.getId())
            .actorId(roleAssignment.getActorId())
            .actorIdType(roleAssignment.getActorIdType() != null ? roleAssignment.getActorIdType().toString() : null)
            .attributes(JacksonUtils.convertValueJsonNode(roleAssignment.getAttributes()))
            .classification(roleAssignment.getClassification() != null ? roleAssignment.getClassification()
                .toString() : null)
            .beginTime(roleAssignment.getBeginTime() != null ? roleAssignment.getBeginTime().toLocalDateTime() : null)
            .endTime(roleAssignment.getEndTime() != null ? roleAssignment.getEndTime().toLocalDateTime() : null)
            .created(roleAssignment.getCreated().toLocalDateTime())
            .grantType(roleAssignment.getGrantType() != null ? roleAssignment.getGrantType().toString() : null)
            .roleName(roleAssignment.getRoleName())
            .roleType(roleAssignment.getRoleType() != null ? roleAssignment.getRoleType().toString() : null)
            .readOnly(roleAssignment.isReadOnly())
            .roleCategory(roleAssignment.getRoleCategory() != null ? roleAssignment.getRoleCategory().toString() : null)
            .authorisations(!CollectionUtils.isEmpty(roleAssignment.getAuthorisations())
                                ? roleAssignment.getAuthorisations().toArray(new String[0]) : null)
            .isNewFlag(isNewFlag)
            .build();
    }

    public RoleAssignment convertHistoryEntityToRoleAssignment(HistoryEntity historyEntity) {
        return RoleAssignment.builder()
            .id(historyEntity.getId())
            .actorIdType(ActorIdType.valueOf(historyEntity.getActorIdType()))
            .actorId(historyEntity.getActorId())
            .classification(Classification.valueOf(historyEntity.getClassification()))
            .grantType(GrantType.valueOf(historyEntity.getGrantType()))
            .readOnly(historyEntity.isReadOnly())
            .roleName(historyEntity.getRoleName())
            .roleType(RoleType.valueOf(historyEntity.getRoleType()))
            .roleCategory(RoleCategory.valueOf(historyEntity.getRoleCategory()))
            .status(Status.valueOf(historyEntity.getStatus()))
            .process(historyEntity.getProcess())
            .reference(historyEntity.getReference())
            .beginTime(historyEntity.getBeginTime() != null ? historyEntity.getBeginTime()
                .atZone(ZoneId.of("UTC")) : null)
            .endTime(historyEntity.getEndTime() != null ? historyEntity.getEndTime().atZone(ZoneId.of("UTC")) : null)
            .created(historyEntity.getCreated().atZone(ZoneId.of("UTC")))
            .log(historyEntity.getLog())
            .attributes(JacksonUtils.convertValue(historyEntity.getAttributes()))
            .notes(historyEntity.getNotes())

            .authorisations(historyEntity.getAuthorisations() != null && historyEntity
                .getAuthorisations().length != 0
                                ? Arrays.asList(historyEntity.getAuthorisations()) :
                                null)
            .build();
    }

    public RoleAssignment convertEntityToRoleAssignment(RoleAssignmentEntity roleAssignmentEntity) {

        return RoleAssignment.builder()
            .id(roleAssignmentEntity.getId())
            .actorIdType(ActorIdType.valueOf(roleAssignmentEntity.getActorIdType()))
            .actorId(roleAssignmentEntity.getActorId())
            .classification(Classification.valueOf(roleAssignmentEntity.getClassification()))
            .grantType(GrantType.valueOf(roleAssignmentEntity.getGrantType()))
            .readOnly(roleAssignmentEntity.isReadOnly())
            .roleName(roleAssignmentEntity.getRoleName())
            .roleType(RoleType.valueOf(roleAssignmentEntity.getRoleType()))
            .roleCategory(roleAssignmentEntity.getRoleCategory() != null ? RoleCategory.valueOf(
                roleAssignmentEntity.getRoleCategory()) : null)
            .beginTime(roleAssignmentEntity.getBeginTime() != null ? roleAssignmentEntity.getBeginTime()
                .atZone(ZoneId.of(
                    "UTC")) : null)
            .endTime(roleAssignmentEntity.getEndTime() != null ? roleAssignmentEntity.getEndTime().atZone(ZoneId.of(
                "UTC")) : null)
            .created(roleAssignmentEntity.getCreated().atZone(ZoneId.of("UTC")))
            .attributes(JacksonUtils.convertValue(roleAssignmentEntity.getAttributes()))
            .authorisations(roleAssignmentEntity.getAuthorisations() != null && roleAssignmentEntity
                .getAuthorisations().length != 0
                                ? Arrays.asList(roleAssignmentEntity.getAuthorisations()) :
                                null)
            .build();
    }

    public HistoryEntity prepareHistoryEntityForPersistance(RoleAssignment roleAssignment, Request request) {
        UUID roleAssignmentId = roleAssignment.getId();
        UUID requestId = request.getId();

        var requestEntity = convertRequestToEntity(request);
        if (requestId != null) {
            requestEntity.setId(requestId);
        }

        var historyEntity = convertRoleAssignmentToHistoryEntity(
            roleAssignment,
            requestEntity
        );
        historyEntity.setId(Objects.requireNonNullElseGet(roleAssignmentId, UUID::randomUUID));
        return historyEntity;
    }

    public ExistingRoleAssignment convertEntityToExistingRoleAssignment(RoleAssignmentEntity roleAssignmentEntity) {

        return ExistingRoleAssignment.builder()
            .id(roleAssignmentEntity.getId())
            .actorIdType(ActorIdType.valueOf(roleAssignmentEntity.getActorIdType()))
            .actorId(roleAssignmentEntity.getActorId())
            .classification(Classification.valueOf(roleAssignmentEntity.getClassification()))
            .grantType(GrantType.valueOf(roleAssignmentEntity.getGrantType()))
            .readOnly(roleAssignmentEntity.isReadOnly())
            .roleName(roleAssignmentEntity.getRoleName())
            .roleType(RoleType.valueOf(roleAssignmentEntity.getRoleType()))
            .roleCategory(roleAssignmentEntity.getRoleCategory() != null ? RoleCategory.valueOf(
                roleAssignmentEntity.getRoleCategory()) : null)
            .beginTime(roleAssignmentEntity.getBeginTime() != null ? roleAssignmentEntity.getBeginTime()
                .atZone(ZoneId.of(
                    "UTC")) : null)
            .endTime(roleAssignmentEntity.getEndTime() != null ? roleAssignmentEntity.getEndTime().atZone(ZoneId.of(
                "UTC")) : null)
            .created(roleAssignmentEntity.getCreated().atZone(ZoneId.of("UTC")))
            .attributes(JacksonUtils.convertValue(roleAssignmentEntity.getAttributes()))
            .authorisations(roleAssignmentEntity.getAuthorisations() != null && roleAssignmentEntity
                .getAuthorisations().length != 0
                                ? Arrays.asList(roleAssignmentEntity.getAuthorisations()) :
                                null)
            .build();
    }

    public FlagConfig convertFlagRequestToFlagConfig(FlagRequest flagRequest) {
        return FlagConfig.builder()
            .flagName(flagRequest.getFlagName())
            .env(flagRequest.getEnv())
            .serviceName(flagRequest.getServiceName())
            .status(flagRequest.getStatus())
            .build();
    }
}
