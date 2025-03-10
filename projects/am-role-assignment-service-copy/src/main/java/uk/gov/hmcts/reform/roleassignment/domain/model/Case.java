package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Data
@Getter
@Setter
@Builder
@ToString
public class Case {

    public static final String CASE_MANAGEMENT_LOCATION = "caseManagementLocation";
    public static final String BASE_LOCATION = "baseLocation";
    public static final String REGION = "region";

    @JsonProperty("id")
    private String id;

    @JsonProperty("jurisdiction")
    private String jurisdiction;

    @JsonProperty("case_type")
    private String caseTypeId;

    @JsonProperty("created_on")
    private LocalDateTime createdOn;

    @JsonProperty("last_modified_on")
    private LocalDateTime lastModifiedOn;

    @JsonProperty("last_state_modified_on")
    private LocalDateTime lastStateModifiedOn;

    @JsonProperty("state")
    private String state;

    @JsonProperty("security_classification")
    //Convert from ccd.SecurityClassification to String
    private Classification securityClassification;

    @JsonProperty("data")
    private Map<String, JsonNode> data;

    @JsonProperty("data_classification")
    private Map<String, JsonNode> dataClassification;

    public String getRegion() {
        if (Objects.nonNull(data) && Objects.nonNull(data.get(CASE_MANAGEMENT_LOCATION))) {
            Optional<JsonNode> caseManagementLocation = Optional.ofNullable(data.get(CASE_MANAGEMENT_LOCATION));
            if (caseManagementLocation.isPresent() && Objects.nonNull(caseManagementLocation.get().get(REGION))) {
                return caseManagementLocation.get().get(REGION).asText();
            }
        }
        return "";
    }

    public String getBaseLocation() {
        if (Objects.nonNull(data) && Objects.nonNull(data.get(CASE_MANAGEMENT_LOCATION))) {
            Optional<JsonNode> caseManagementLocation = Optional.ofNullable(data.get(CASE_MANAGEMENT_LOCATION));
            if (caseManagementLocation.isPresent()
                && Objects.nonNull(caseManagementLocation.get().get(BASE_LOCATION))) {
                return caseManagementLocation.get().get(BASE_LOCATION).asText();
            }
        }
        return "";
    }

}
