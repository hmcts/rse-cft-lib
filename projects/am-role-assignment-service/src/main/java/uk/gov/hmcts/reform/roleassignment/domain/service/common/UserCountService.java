package uk.gov.hmcts.reform.roleassignment.domain.service.common;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

@Slf4j
@Service
public class UserCountService {

    static final String JURISDICTION_FILTER_KEY = "jurisdictionFilter";
    static final String RESULTS_KEY = "results";
    static final String TIMESTAMP_KEY = "timestamp";

    @Autowired
    private TelemetryClient telemetryClient;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    public Map<String, Object> getOrgUserCount() throws JsonProcessingException {

        String timestamp = LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();

        // user count by jurisdiction
        List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount> orgUserCountByJurisdiction =
            roleAssignmentRepository.getOrgUserCountByJurisdiction();
        Map<String, String> properties = Map.of(
            RESULTS_KEY, ow.writeValueAsString(orgUserCountByJurisdiction),
            TIMESTAMP_KEY, timestamp
        );
        telemetryClient.trackEvent("orgUserCountByJurisdiction", properties,null);
        Map<String, Object> countsByJurisdiction = Map.of("OrgUserCountByJurisdiction", orgUserCountByJurisdiction);
        log.debug(ow.writeValueAsString(countsByJurisdiction));


        // user count by jurisdiction and role name
        List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount> orgUserCountByJurisdictionAndRoleName =
            roleAssignmentRepository.getOrgUserCountByJurisdictionAndRoleName();
        List<Map<String, String>> eventList = getEventMapList(orgUserCountByJurisdictionAndRoleName, timestamp);
        eventList.stream().forEach(
            event -> telemetryClient.trackEvent("orgUserCountByJurisdictionAndRoleName", event,null));
        Map<String, Object> countsByJurisdictionAndRoleName = Map.of(
            "OrgUserCountByJurisdictionAndRoleName", orgUserCountByJurisdictionAndRoleName);
        log.debug(ow.writeValueAsString(countsByJurisdictionAndRoleName));

        return Map.of(
            "OrgUserCountByJurisdiction", orgUserCountByJurisdiction,
            "OrgUserCountByJurisdictionAndRoleName", orgUserCountByJurisdictionAndRoleName);
    }

    public List<Map<String, String>> getEventMapList(
        List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount> rows,  String timestamp)
        throws JsonProcessingException {

        List<String> jurisdictions = getDistinctJurisdictions(rows);
        List<Map<String, String>> eventList = new ArrayList<>();
        ObjectWriter ow = new ObjectMapper().writer();

        for (String jurisdiction : jurisdictions) {
            List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount> jurisdictionRows =
                filterRowsByJurisdiction(rows, jurisdiction);
            String results = ow.writeValueAsString(jurisdictionRows);
            eventList.add(getEventMap(jurisdiction,results,timestamp));
        }

        return eventList;
    }

    private Map<String, String> getEventMap(String jurisdiction, String results, String timestamp) {
        Map<String, String> eventMap;
        if (jurisdiction != null) {
            eventMap = Map.of(
                JURISDICTION_FILTER_KEY, jurisdiction,
                RESULTS_KEY, results,
                TIMESTAMP_KEY, timestamp);
        } else {
            eventMap = Map.of(
                JURISDICTION_FILTER_KEY, "NULL",
                RESULTS_KEY, results,
                TIMESTAMP_KEY, timestamp);
        }
        return eventMap;
    }

    private List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount> filterRowsByJurisdiction(
        List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount> rows, String jurisdiction) {
        return jurisdiction != null ? rows.stream().filter(r -> r.getJurisdiction() != null
            && r.getJurisdiction().equals(jurisdiction)).toList() :
            rows.stream().filter(r -> r.getJurisdiction() == null).toList();
    }

    private List<String> getDistinctJurisdictions(
        List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount> rows) {
        Set<String> jurisdictions = new HashSet<>(rows.size());
        rows.stream().forEach(r -> jurisdictions.add(r.getJurisdiction()));
        return new ArrayList<>(jurisdictions);
    }
}
