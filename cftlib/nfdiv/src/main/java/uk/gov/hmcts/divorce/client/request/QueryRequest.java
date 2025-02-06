package uk.gov.hmcts.divorce.client.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import uk.gov.hmcts.divorce.sow014.lib.GrantType;
import uk.gov.hmcts.reform.ccd.client.model.Classification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@EqualsAndHashCode
@Builder
@JsonNaming
@JsonInclude(Include.NON_NULL)
@ToString
@SuppressWarnings("PMD.LinguisticNaming")
public final class QueryRequest {

    private final List<String> actorId;
    private final List<RoleType> roleType;
    private final List<String> roleName;
    private final List<Classification> classification;
    private final List<GrantType> grantType;
    private final LocalDateTime validAt;
    private final List<RoleCategory> roleCategory;
    private final Map<String, List<String>> attributes;
    private final List<String> authorisations;
    private final List<String> hasAttributes;
    private final Boolean readOnly;

    public List<String> getActorId() {
        return actorId;
    }

    public List<RoleType> getRoleType() {
        return roleType;
    }

    public List<String> getRoleName() {
        return roleName;
    }

    public List<Classification> getClassification() {
        return classification;
    }

    public List<GrantType> getGrantType() {
        return grantType;
    }

    public LocalDateTime getValidAt() {
        return validAt;
    }

    public List<RoleCategory> getRoleCategory() {
        return roleCategory;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public List<String> getAuthorisations() {
        return authorisations;
    }

    public List<String> getHasAttributes() {
        return hasAttributes;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }
}
