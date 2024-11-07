package uk.gov.hmcts.divorce.sow014;

import ch.qos.logback.core.util.StringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.sql.Types;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;

import java.util.Map;
import java.util.Optional;

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
    public ResponseEntity<String> createEvent(@RequestBody POCCaseDetails event) {
        log.info("case Details: {}", event);

        Map<String, Object> caseDetails = event.getCaseDetails();
        int version = (int) Optional.ofNullable(event.getCaseDetails().get("version")).orElse(1);
        // Upsert the case - create if it doesn't exist, update if it does.
        // TODO: Optimistic lock; throw an exception if the version is out of date (ie. zero rows changed in resultset).
        var rowsAffected = db.update(
            """
                insert into case_data (jurisdiction, case_type_id, state, data, data_classification, reference, security_classification, version)
                values (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::securityclassification, ?)
                on conflict (reference)
                do update set
                    state = excluded.state,
                    data = excluded.data,
                    data_classification = excluded.data_classification,
                    security_classification = excluded.security_classification,
                    version = case_data.version + 1
                    WHERE case_data.version = EXCLUDED.version;
                    """,
            "DIVORCE",
            "NFD",
            caseDetails.get("state"),
            mapper.writeValueAsString(caseDetails.get("case_data")),
            mapper.writeValueAsString(caseDetails.get("data_classification")),
            caseDetails.get("id"),
            caseDetails.get("security_classification"),
            version
        );
        if (rowsAffected != 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflict: Case concurrently modified");
        }

        saveAuditRecord(event, 1);

        String response = getCase((Long) caseDetails.get("id"));
        log.info("case response: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping(
            value = "/cases/{caseRef}/history",
            produces = "application/json"
    )
    public String loadHistory(@PathVariable("caseRef") long caseRef) {
        return db.queryForObject(
                """
                         select jsonb_agg(to_jsonb(e) - 'case_reference' - 'event_id'
                         || jsonb_build_object('case_data_id', case_reference)
                         || jsonb_build_object('event_instance_id', id)
                         || jsonb_build_object('id', event_id)
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
                          summary,
                          description,
                          security_classification)
                        values (?::jsonb,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?,?::securityclassification)
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
                event.getSummary(),
                event.getDescription(),
                data.get("security_classification")
        );
    }
}
