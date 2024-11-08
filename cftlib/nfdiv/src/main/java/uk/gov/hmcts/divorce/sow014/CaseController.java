package uk.gov.hmcts.divorce.sow014;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.ccd.sdk.runtime.CallbackController;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

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

    @Autowired
    private CallbackController runtime;

    @GetMapping(
            value = "/cases/{caseRef}",
            produces = "application/json"
    )
    public String getCase(@PathVariable("caseRef") long caseRef) {
        return db.queryForObject(
                """
                        select
    (((r - 'data') - 'marked_by_logstash') - 'reference') - 'resolved_ttl'
    || jsonb_build_object('case_data', (
    r->'data'
    || jsonb_build_object('notes', notes)
    ))
    || jsonb_build_object('id', reference)
    from (
      select
        reference,
        coalesce(n.notes, '[]'::jsonb) as notes,
         to_jsonb(c) r
       from case_data c left join notes_by_case n using(reference)
       where reference = ?

    ) s
                        """,
                new Object[]{caseRef}, String.class);
    }

    @SneakyThrows
    @PostMapping("/cases")
    public ResponseEntity<String> createEvent(@RequestBody POCCaseDetails event) {
        log.info("case Details: {}", event);

        dispatchAboutToSubmit(event);
        saveCase(event);
        dispatchSubmitted(event);

        String response = getCase((Long) event.getCaseDetails().get("id"));
        log.info("case response: {}", response);
        return ResponseEntity.ok(response);
    }

    private void dispatchSubmitted(POCCaseDetails event) {
        try {
            var req = CallbackRequest.builder()
                .caseDetails(toCaseDetails(event.getCaseDetails()))
                .caseDetailsBefore(toCaseDetails(event.getCaseDetailsBefore()))
                .eventId(event.getEventDetails().getEventId())
                .build();
            runtime.submitted(req);
        } catch (NoSuchMethodError e) {
            // TODO: There is a config generator classpath bug - this exception is thrown when a callback doesn't exist for an event!
            // There's nothing to do anyway if there's no callback so deferred for now.
        } catch (Throwable e) {
            log.error("Error in submitted callback", e);
        }
    }

    private void saveCase(POCCaseDetails event) throws Exception {
        Map<String, Object> caseDetails = event.getCaseDetails();
        var state = event.getEventDetails().getStateId() != null
            ? event.getEventDetails().getStateId()
            : caseDetails.get("state");
        int version = (int) Optional.ofNullable(event.getCaseDetails().get("version")).orElse(1);
        // Upsert the case - create if it doesn't exist, update if it does.
        // TODO: Optimistic lock; throw an exception if the version is out of date (ie. zero rows changed in resultset).
        var rowsAffected = db.update(
            """
                insert into case_data (jurisdiction, case_type_id, state, data, data_classification, reference, security_classification, version)
                -- TODO: separate private data model from public view so we don't duplicate eg. notes in the json
                values (?, ?, ?, (?::jsonb - 'notes'), ?::jsonb, ?, ?::securityclassification, ?)
                on conflict (reference)
                do update set
                    state = excluded.state,
                    data = excluded.data,
                    data_classification = excluded.data_classification,
                    security_classification = excluded.security_classification,
                    version = case
                                when case_data.data is distinct from excluded.data then case_data.version + 1
                                else case_data.version
                              end
                    WHERE case_data.version = EXCLUDED.version;
                    """,
            "DIVORCE",
            "NFD",
            state,
            mapper.writeValueAsString(caseDetails.get("case_data")),
            mapper.writeValueAsString(caseDetails.get("data_classification")),
            caseDetails.get("id"),
            caseDetails.get("security_classification"),
            version
        );
        if (rowsAffected != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case was updated concurrently");
        }

        saveAuditRecord(event, 1);
    }

    @SneakyThrows
    private POCCaseDetails dispatchAboutToSubmit(POCCaseDetails event) {
        try {
            var req = CallbackRequest.builder()
                .caseDetails(toCaseDetails(event.getCaseDetails()))
                .caseDetailsBefore(toCaseDetails(event.getCaseDetailsBefore()))
                .eventId(event.getEventDetails().getEventId())
                .build();
            var cb = runtime.aboutToSubmit(req);

            event.getCaseDetails().put("case_data", mapper.readValue(mapper.writeValueAsString(cb.getData()), Map.class));
            if (cb.getState() != null) {
                event.getEventDetails().setStateId(cb.getState().toString());
            }
            return event;
        } catch (NoSuchMethodError e) {
            // TODO: There is a config generator classpath bug - this exception is thrown when a callback doesn't exist for an event!
            // There's nothing to do anyway if there's no callback so deferred for now.
            return event;
        }
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
        var caseView = getCase((Long) details.getCaseDetails().get("id"));
        var currentView = mapper.readValue(caseView, Map.class);
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
                mapper.writeValueAsString(currentView.get("case_data")),
                mapper.writeValueAsString(currentView.get("data_classification")),
                event.getEventId(),
                "user-id",
                currentView.get("id"),
                "NFD",
                version,
                currentView.get("state"),
                "a-first-name",
                "a-last-name",
                event.getEventName(),
                event.getStateName(),
                event.getSummary(),
                event.getDescription(),
                currentView.get("security_classification")
        );
    }
    @SneakyThrows
    private CaseDetails toCaseDetails(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        return mapper.readValue(mapper.writeValueAsString(data), CaseDetails.class);
    }
}
