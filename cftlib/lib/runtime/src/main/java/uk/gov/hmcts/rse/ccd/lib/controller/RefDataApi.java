package uk.gov.hmcts.rse.ccd.lib.controller;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.rse.ccd.lib.RefData;

/**
 * A stand-in for the reference data ExUI reads, with the refData feature: rd-commondata-api's lists of values and case
 * flags, rd-location-ref-api's court venues and rd-judicial-api's judges. The data is in {@link RefData}.
 */
@Conditional(RefDataOnly.class)
@RestController
public class RefDataApi {

    private final RefData refData = RefData.fromEnvironment();

    @GetMapping(value = "/refdata/commondata/lov/categories/{categoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List<ObjectNode>> listOfValues(
        @PathVariable("categoryId") String categoryId,
        @RequestParam(value = "serviceId", required = false) String serviceId,
        @RequestParam(value = "parentCategory", required = false) String parentCategory,
        @RequestParam(value = "parentKey", required = false) String parentKey,
        @RequestParam(value = "key", required = false) String key,
        @RequestParam(value = "isChildRequired", required = false) String isChildRequired) {
        // rd-commondata-api answers a category it has no rows of with a 404. An empty list keeps ExUI's journeys going
        // when the service hasn't supplied a list they read.
        return Map.of("list_of_values",
            refData.listOfValues(categoryId, blankAsNull(serviceId), blankAsNull(parentCategory),
                blankAsNull(parentKey), blankAsNull(key), isChildRequired));
    }

    @GetMapping(value = "/refdata/commondata/caseflags/service-id={serviceId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> caseFlags(
        @PathVariable("serviceId") String serviceId,
        @RequestParam(value = "flag-type", required = false) String flagType,
        @RequestParam(value = "welsh-required", required = false) String welshRequired,
        @RequestParam(value = "available-external-flag", required = false) String availableExternalFlag) {
        return refData.caseFlags(serviceId, flagType, welshRequired, availableExternalFlag)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/refdata/location/court-venues", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<JsonNode> courtVenues(@RequestParam Map<String, String> query) {
        return refData.courtVenues(query);
    }

    @GetMapping(value = "/refdata/location/court-venues/venue-search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<JsonNode> searchCourtVenues(@RequestParam Map<String, String> query) {
        return refData.searchCourtVenues(query);
    }

    @PostMapping(value = "/refdata/judicial/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ObjectNode> judicialUsers(@RequestBody JsonNode request) {
        return refData.judicialUsers(request);
    }

    @PostMapping(value = "/refdata/judicial/users/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ObjectNode> searchJudicialUsers(@RequestBody JsonNode request) {
        return refData.searchJudicialUsers(request);
    }

    private static String blankAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
