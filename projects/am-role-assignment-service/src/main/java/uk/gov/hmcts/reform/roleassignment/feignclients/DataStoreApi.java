package uk.gov.hmcts.reform.roleassignment.feignclients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiConfiguration;
import uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback;

@FeignClient(value = "datastoreclient", url = "${feign.client.config.datastoreclient.url}",
             configuration = DataStoreApiConfiguration.class,
             fallback = DataStoreApiFallback.class)

public interface DataStoreApi {

    @GetMapping(value = "/")
    String getServiceStatus();

    @GetMapping(value = "/cases/{caseId}", headers = "experimental=true")
    Case getCaseDataV2(@PathVariable("caseId") String caseId);

}
