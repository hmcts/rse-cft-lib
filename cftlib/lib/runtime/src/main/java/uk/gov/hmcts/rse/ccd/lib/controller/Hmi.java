package uk.gov.hmcts.rse.ccd.lib.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.rse.ccd.lib.Hearings;

/**
 * A stand-in for HMI, and for the Azure AD tenant HMC authenticates to it with, for the hmc feature's outbound
 * adapter. HMI accepts a hearing request with a 202 and lists it later, which tests do with sendHmiListing.
 */
@RestController
@RequestMapping("/hmi")
public class Hmi {

    @PostMapping("/oauth2/token")
    public Map<String, Object> token() {
        return Map.of("access_token", "cftlib-hmi", "token_type", "Bearer", "expires_in", 3600);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/hearings")
    public ResponseEntity<Map<String, Object>> requestHearing(@RequestBody String body) {
        return respond(Hearings.receive("POST", null, body));
    }

    @PutMapping("/hearings/{caseListingRequestId}")
    public ResponseEntity<Map<String, Object>> amendHearing(@PathVariable("caseListingRequestId") String id,
                                                            @RequestBody String body) {
        return respond(Hearings.receive("PUT", id, body));
    }

    @DeleteMapping("/hearings/{caseListingRequestId}")
    public ResponseEntity<Map<String, Object>> deleteHearing(@PathVariable("caseListingRequestId") String id,
                                                             @RequestBody(required = false) String body) {
        return respond(Hearings.receive("DELETE", id, body));
    }

    private ResponseEntity<Map<String, Object>> respond(Hearings.Rejection rejection) {
        if (rejection != null) {
            return ResponseEntity.badRequest()
                .body(Map.of("errCode", rejection.errorCode(), "errorDesc", rejection.description()));
        }
        return ResponseEntity.accepted().body(Map.of("response code", 202, "description", "Request accepted"));
    }
}
