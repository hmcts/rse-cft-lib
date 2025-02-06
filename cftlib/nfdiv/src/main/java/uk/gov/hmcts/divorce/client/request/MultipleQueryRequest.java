package uk.gov.hmcts.divorce.client.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@Builder
@JsonNaming
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class MultipleQueryRequest {
    private List<QueryRequest> queryRequests;

    public List<QueryRequest> getQueryRequests() {
        return queryRequests;
    }
}
