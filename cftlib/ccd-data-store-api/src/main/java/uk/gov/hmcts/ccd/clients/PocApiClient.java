package uk.gov.hmcts.ccd.clients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.hmcts.ccd.customheaders.UserAuthHeadersInterceptorConfig;
import uk.gov.hmcts.ccd.domain.model.aggregated.POCCaseEvent;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.std.AuditEvent;

@FeignClient(name = "pocApi", url = "${poc.apis.url}", configuration = UserAuthHeadersInterceptorConfig.class)
public interface PocApiClient {

    @GetMapping(value = "/ccd/cases/{case-ref}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    CaseDetails getCase(@PathVariable("case-ref") String caseRef, @RequestHeader String roleAssignments);

    @PostMapping(value = "/ccd/cases", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    CaseDetails createEvent(@RequestBody POCCaseEvent caseEvent, @RequestHeader String roleAssignments);

    @GetMapping(value = "/ccd/cases/{case-ref}/history", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    List<AuditEvent> getEvents(@PathVariable("case-ref") String caseReference, @RequestHeader String roleAssignments);
}
