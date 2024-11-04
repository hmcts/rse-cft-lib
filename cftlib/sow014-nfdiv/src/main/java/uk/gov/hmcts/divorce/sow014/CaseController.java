package uk.gov.hmcts.divorce.sow014;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping(path = "/ccd")
public class CaseController {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    @GetMapping(
            value = "/cases/{caseRef}",
            produces = "application/json"
    )
    public String getCase(@PathVariable("caseRef") long caseRef) {
        return db.queryForObject(
                """
                        select
                        (((r - 'data') - 'marked_by_logstash') - 'reference') - 'resolved_ttl'
                        || jsonb_build_object('case_data', r->'data')
                        || jsonb_build_object('id', reference)
                        from (
                        select reference, to_jsonb(c) r from case_data c where reference = ?
                        ) s""",
                new Object[]{caseRef}, String.class);
    }

    @SneakyThrows
    @PostMapping("/cases")
    public String createEvent(@RequestBody POCCaseDetails event) {
        log.info("case Details: {}", event);
        db.update(
            """
                insert into case_data (jurisdiction, case_type_id, state, data, data_classification, reference, security_classification, version)
                values (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::securityclassification, ?)
                """,
            "DIVORCE",
            "NFD",
            event.getCaseDetails().get("state"),
            mapper.writeValueAsString(event.getCaseDetails().get("case_data")),
            mapper.writeValueAsString(event.getCaseDetails().get("data_classification")),
            event.getCaseDetails().get("id"),
            event.getCaseDetails().get("security_classification"),
            1
        );
        saveAuditRecord(event, 1);
        String response = getCase((long) event.getCaseDetails().get("id"));
        log.info("case response: {}", response);
        return response;
    }

    @GetMapping(
            value = "/cases/{caseRef}/history",
            produces = "application/json"
    )
    public String loadHistory(@PathVariable("caseRef") long caseRef) {
        return db.queryForObject(
                """
                        select jsonb_agg(to_jsonb(e) - 'event_id' - 'case_reference'
                        || jsonb_build_object('id', event_id)
                       -- || jsonb_build_object('internal_id', id)
                         order by id desc)
                        from case_event e
                        where case_reference = ?
                        """,
                new Object[]{caseRef}, String.class);
    }

    @SneakyThrows
    private void saveAuditRecord(POCCaseDetails details, int version) {
        var event = details.getEventDetails();
        var data = details.getCaseDetails();
        db.update(
                """
                    insert into case_event (
                      data,
                      data_classification,
                      event_id,
                      user_id,
                      case_reference,
                      case_type_id,
                      case_type_version,
                      state_id,
                      user_first_name,
                      user_last_name,
                      event_name,
                      state_name,
                      security_classification)
                    values (?::jsonb,?::jsonb,?,?,?,?,?,?,?,?,?,?,?::securityclassification)
                    """,
                mapper.writeValueAsString(data.get("case_data")),
                mapper.writeValueAsString(data.get("data_classification")),
                event.getEventId(),
                "user-id",
                data.get("id"),
                "NFD",
                version,
                data.get("state"),
                "a-first-name",
                "a-last-name",
                event.getEventName(),
                event.getStateName(),
                data.get("security_classification")
        );
    }
}
