package uk.gov.hmcts.rse.ccd.lib.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import uk.gov.hmcts.rse.ccd.lib.Hearings;

/**
 * The runtime's /hmi page, and the API behind it, for answering hearing requests as List Assist would by hand: list
 * a hearing, confirm its cancellation, report that it cannot be listed, or have HMI reject the next request. Each
 * action posts a form from the page, or can be called with curl or from a UI test; a browser is sent back to the page.
 */
@RestController
@RequestMapping("/hmi")
public class HmiConsole {

    static final int DEFAULT_ERROR_CODE = 2000;
    static final String DEFAULT_ERROR = "cftlib's HMI could not list the hearing";

    // HMC's statuses in which it accepts a listing for a hearing.
    private static final Set<String> LISTABLE = Set.of("AWAITING_LISTING", "UPDATE_REQUESTED", "UPDATE_SUBMITTED",
        "LISTED");
    private static final Set<String> CANCELLING = Set.of("CANCELLATION_REQUESTED", "CANCELLATION_SUBMITTED");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> page(@RequestParam(name = "message", required = false) String message) {
        return blocking(() -> render(Hearings.ready(), message));
    }

    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> hearings() {
        return blocking(() -> {
            var hearings = Hearings.ready();
            if (hearings == null) {
                return notReady();
            }
            return ResponseEntity.ok(hearings.hearings().stream().map(HmiConsole::json).toList());
        });
    }

    @PostMapping("/requests/{hearingId}/listing")
    public Mono<ResponseEntity<Object>> list(@PathVariable("hearingId") String hearingId,
                                             ServerWebExchange exchange) {
        return act(exchange, (hearings, params) -> {
            var start = startTime(params.getFirst("start"));
            hearings.list(hearingId, start);
            return "Sent the listing for hearing " + hearingId + ".";
        });
    }

    @PostMapping("/requests/{hearingId}/cancellation")
    public Mono<ResponseEntity<Object>> cancel(@PathVariable("hearingId") String hearingId,
                                               ServerWebExchange exchange) {
        return act(exchange, (hearings, params) -> {
            hearings.cancel(hearingId);
            return "Sent the cancellation for hearing " + hearingId + ".";
        });
    }

    @PostMapping("/requests/{hearingId}/exception")
    public Mono<ResponseEntity<Object>> raiseException(@PathVariable("hearingId") String hearingId,
                                                       ServerWebExchange exchange) {
        return act(exchange, (hearings, params) -> {
            hearings.raiseException(hearingId, errorCode(params), description(params));
            return "Reported that hearing " + hearingId + " cannot be listed.";
        });
    }

    @PostMapping("/rejections")
    public Mono<ResponseEntity<Object>> rejectNext(ServerWebExchange exchange) {
        return act(exchange, (hearings, params) -> {
            var caseReference = params.getFirst("caseReference");
            if (caseReference == null || caseReference.isBlank()) {
                hearings.rejectNextHmiRequest(errorCode(params), description(params));
                return "HMI will reject the next hearing request.";
            }
            hearings.rejectNextHmiRequest(caseReference.trim(), errorCode(params), description(params));
            return "HMI will reject the next hearing request for case " + caseReference.trim() + ".";
        });
    }

    @PostMapping("/auto-respond")
    public Mono<ResponseEntity<Object>> autoRespond(ServerWebExchange exchange) {
        return act(exchange, (hearings, params) -> {
            var enabled = Boolean.parseBoolean(params.getFirst("enabled"));
            Hearings.autoRespond(enabled);
            return enabled
                ? "HMI will list new and amended hearings, and confirm cancellations, by itself."
                : "HMI will wait to be told how to answer each hearing request.";
        });
    }

    interface Action {
        String run(Hearings hearings, MultiValueMap<String, String> params);
    }

    // Takes its parameters from the query or a form, and answers a browser by sending it back to the page.
    private Mono<ResponseEntity<Object>> act(ServerWebExchange exchange, Action action) {
        return exchange.getFormData().flatMap(form -> blocking(() -> {
            var params = new LinkedMultiValueMap<String, String>();
            params.addAll(exchange.getRequest().getQueryParams());
            params.addAll(form);
            var browser = exchange.getRequest().getHeaders().getAccept().stream()
                .anyMatch(type -> type.isCompatibleWith(MediaType.TEXT_HTML) && !type.isWildcardType());
            var hearings = Hearings.ready();
            if (hearings == null) {
                return browser ? backToPage("The hmc feature has not started yet.") : notReady();
            }
            try {
                var message = action.run(hearings, params);
                return browser ? backToPage(message) : ResponseEntity.accepted().body(Map.of("message", message));
            } catch (Exception e) {
                // Includes the checked exceptions the inbound adapter's client and HMC's database throw, such as when
                // the adapter is restarting.
                return browser ? backToPage("Failed: " + e.getMessage())
                    : ResponseEntity.badRequest().body(Map.of("message", String.valueOf(e.getMessage())));
            }
        }));
    }

    private static ResponseEntity<Object> backToPage(String message) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .header(HttpHeaders.LOCATION, "/hmi?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8))
            .build();
    }

    private static ResponseEntity<Object> notReady() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "The hmc feature is off, or has not started yet."));
    }

    private static <T> Mono<T> blocking(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    private static LocalDateTime startTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("start must be a date and time such as 2026-12-01T10:00, not " + value);
        }
    }

    private static int errorCode(MultiValueMap<String, String> params) {
        var value = params.getFirst("errorCode");
        if (value == null || value.isBlank()) {
            return DEFAULT_ERROR_CODE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("errorCode must be a number, not " + value);
        }
    }

    private static String description(MultiValueMap<String, String> params) {
        var value = params.getFirst("description");
        return value == null || value.isBlank() ? DEFAULT_ERROR : value.trim();
    }

    private static Map<String, Object> json(Hearings.HmiHearing hearing) {
        var value = new LinkedHashMap<String, Object>();
        value.put("hearingId", hearing.hearingId());
        value.put("caseReference", hearing.caseReference());
        value.put("caseTitle", hearing.caseTitle());
        value.put("hmcStatus", hearing.hmcStatus());
        value.put("lastRequest", hearing.lastRequest());
        value.put("lastReceived", hearing.lastReceived().toString());
        value.put("suggestedStart", hearing.suggestedStart() == null ? null : hearing.suggestedStart().toString());
        value.put("venue", hearing.venue());
        value.put("duration", hearing.duration());
        value.put("judge", hearing.judge());
        value.put("unreadable", hearing.unreadable());
        return value;
    }

    static String render(Hearings hearings, String message) {
        var html = new StringBuilder();
        html.append("""
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>cftlib HMI</title>
            <style>
              body { font-family: Arial, sans-serif; margin: 0 16px 32px; color: #0b0c0c; background: #fff; }
              h1 { font-size: 1.6em; margin: 24px 0 4px; }
              h2 { font-size: 1.2em; margin-top: 28px; }
              p.hint { color: #505a5f; margin-top: 0; max-width: 60em; }
              .message { background: #f3f2f1; border-left: 5px solid #1d70b8; padding: 8px 12px; margin: 16px 0; }
              .failed { border-left-color: #d4351c; }
              table { border-collapse: collapse; width: 100%; }
              th, td { text-align: left; vertical-align: top; padding: 8px 8px 8px 0; }
              td, th { border-bottom: 1px solid #b1b4b6; }
              th { font-size: 0.9em; }
              td form { display: flex; flex-wrap: wrap; gap: 4px; margin: 0 0 6px; }
              input, button { font: inherit; font-size: 0.9em; }
              input[type=number] { width: 6em; }
              button { background: #00703c; color: #fff; border: 0; padding: 4px 10px; cursor: pointer; }
              button.secondary { background: #f3f2f1; color: #0b0c0c; border: 1px solid #b1b4b6; }
              button.warning { background: #d4351c; }
              .status { font-weight: bold; white-space: nowrap; }
              .muted { color: #505a5f; font-size: 0.9em; }
              .table-wrap { overflow-x: auto; }
            </style>
            </head>
            <body>
            <h1>cftlib HMI</h1>
            <p class="hint">Stands in for HMI and List Assist. HMC sends each hearing request here, and this page
            answers it as List Assist would: list the hearing, confirm its cancellation, or report that it cannot
            be listed. HMC then updates the hearing and tells the service on the hmc-to-cft topic.</p>
            """);
        if (message != null && !message.isBlank()) {
            html.append("<div class=\"message").append(message.startsWith("Failed") ? " failed" : "")
                .append("\" role=\"status\">").append(escape(message)).append("</div>\n");
        }
        if (hearings == null) {
            html.append("<p>The hmc feature is off, or has not started yet. Turn it on with <code>features 'hmc'</code>"
                + " on the bootWithCCD task, or <code>RSE_LIB_FEATURES=hmc</code>.</p></body></html>");
            return html.toString();
        }

        var auto = Hearings.autoResponding();
        html.append("<h2>Automatic responses</h2>\n<form method=\"post\" action=\"/hmi/auto-respond\">")
            .append("<input type=\"hidden\" name=\"enabled\" value=\"").append(!auto).append("\">")
            .append(auto
                ? "<p>On: HMI lists new and amended hearings, and confirms cancellations, by itself. "
                : "<p>Off: hearing requests wait here to be answered. ")
            .append("<button class=\"secondary\">").append(auto ? "Turn off" : "Turn on").append("</button></p>")
            .append("</form>\n");

        html.append("""
            <h2>Reject the next request</h2>
            <p class="hint">HMI answers the next hearing request, for any case or for one case, with a 400, as it does a
            request it cannot accept. HMC puts the hearing in EXCEPTION.</p>
            <form method="post" action="/hmi/rejections">
              <input name="caseReference" placeholder="Case reference (optional)" aria-label="Case reference">
            """)
            .append("  <input type=\"number\" name=\"errorCode\" value=\"").append(DEFAULT_ERROR_CODE)
            .append("\" aria-label=\"Error code\">\n")
            .append("  <input name=\"description\" size=\"40\" value=\"").append(escape(DEFAULT_ERROR))
            .append("\" aria-label=\"Error description\">\n")
            .append("  <button class=\"warning\">Reject the next request</button>\n</form>\n");
        List<String> pending = hearings.pendingRejections();
        if (!pending.isEmpty()) {
            html.append("<p class=\"muted\">Waiting to reject: ");
            html.append(String.join("; ", pending.stream().map(HmiConsole::escape).toList())).append("</p>\n");
        }

        var requested = hearings.hearings();
        html.append("<h2>Hearing requests</h2>\n<p class=\"hint\"><a href=\"/hmi\">Refresh</a> to see HMC's latest "
            + "status after answering a request.</p>\n");
        if (requested.isEmpty()) {
            html.append("<p>HMC has not sent HMI any hearing requests yet.</p>\n</body></html>");
            return html.toString();
        }
        html.append("<div class=\"table-wrap\"><table>\n<thead><tr><th>Hearing</th><th>Case</th>"
            + "<th>Status in HMC</th><th>Requested</th><th>Answer</th></tr></thead>\n<tbody>\n");
        for (var hearing : requested) {
            html.append("<tr><td>").append(escape(hearing.hearingId())).append("</td><td>");
            if (hearing.caseReference() != null) {
                html.append("<a href=\"").append(escape(Hearings.caseUrl(hearing.caseReference()))).append("\">")
                    .append(escape(hearing.caseReference()))
                    .append("</a>");
            }
            if (hearing.caseTitle() != null) {
                html.append("<br><span class=\"muted\">").append(escape(hearing.caseTitle())).append("</span>");
            }
            html.append("</td><td class=\"status\">").append(escape(hearing.hmcStatus())).append("</td><td>")
                .append(escape(hearing.lastRequest())).append(" at ").append(TIME.format(hearing.lastReceived()))
                .append("<br><span class=\"muted\">");
            if (hearing.venue() != null) {
                html.append("venue ").append(escape(hearing.venue())).append(", ");
            }
            html.append(hearing.duration()).append(" min");
            if (hearing.judge() != null) {
                html.append(", judge ").append(escape(hearing.judge()));
            }
            if (hearing.unreadable() != null) {
                html.append("<br>Could not read HMC's request: ").append(escape(hearing.unreadable()));
            }
            html.append("</span></td><td>").append(actions(hearing)).append("</td></tr>\n");
        }
        html.append("</tbody></table></div>\n</body></html>");
        return html.toString();
    }

    private static String actions(Hearings.HmiHearing hearing) {
        var id = escape(hearing.hearingId());
        var status = hearing.hmcStatus();
        var actions = new StringBuilder();
        if ("HEARING_REQUESTED".equals(status)) {
            return "<span class=\"muted\">Waiting for HMC to record that HMI accepted the request. Refresh.</span>";
        }
        if (LISTABLE.contains(status) && !"DELETE".equals(hearing.lastRequest())) {
            actions.append("<form method=\"post\" action=\"/hmi/requests/").append(id).append("/listing\">")
                .append("<input type=\"datetime-local\" name=\"start\" aria-label=\"Hearing start, UK time\" value=\"")
                .append(hearing.suggestedStart() == null ? "" : hearing.suggestedStart().toString()).append("\">")
                .append("<button>List</button></form>");
        }
        if (CANCELLING.contains(status)) {
            actions.append("<form method=\"post\" action=\"/hmi/requests/").append(id).append("/cancellation\">")
                .append("<button>Confirm cancellation</button></form>");
        }
        if (LISTABLE.contains(status) || CANCELLING.contains(status)) {
            actions.append("<form method=\"post\" action=\"/hmi/requests/").append(id).append("/exception\">")
                .append("<input type=\"number\" name=\"errorCode\" aria-label=\"Error code\" value=\"")
                .append(DEFAULT_ERROR_CODE).append("\">")
                .append("<input name=\"description\" aria-label=\"Error description\" value=\"")
                .append(escape(DEFAULT_ERROR)).append("\">")
                .append("<button class=\"warning\">Cannot list</button></form>");
        }
        return actions.isEmpty() ? "<span class=\"muted\">Nothing to answer.</span>" : actions.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
