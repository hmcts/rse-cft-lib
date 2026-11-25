package uk.gov.hmcts.rse.ccd.lib;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The responses List Assist sends HMC, through HMI and the inbound adapter's PUT /listings/{hearingId}, built from the
 * request HMC sent for the hearing, so a hearing can be listed or cancelled without writing them by hand.
 *
 * <p>HMC's dates and times are in UTC. The times here, such as when to list a hearing, are court time, the UK's, as
 * List Assist shows them; they're converted from and to UTC at the edges.
 */
final class HmiResponses {

    // List Assist's hearing case statuses, which HMC maps to its own.
    static final String LISTED = "100";
    static final String CLOSED = "8";

    static final ZoneId COURT_TIME = ZoneId.of("Europe/London");
    static final LocalTime START_OF_DAY = LocalTime.of(10, 0);
    static final int DEFAULT_DURATION_MINUTES = 60;

    private HmiResponses() {
    }

    /**
     * What List Assist knows of a request HMC sent it.
     *
     * @param caseReference the case the hearing is for
     * @param caseTitle     the case's name, as HMC sent it
     * @param version       the hearing request's version, which a listing must give for HMC to accept it
     * @param venue         the court the hearing was requested at: the first court location, else the case's court
     * @param earliest      the first date-time, in court time, the hearing can be listed on, if the request gives one
     * @param duration      the hearing's duration in minutes
     * @param judge         the judge the request asks for, if any
     */
    record Request(String caseReference, String caseTitle, int version, String venue, LocalDateTime earliest,
                   int duration, String judge) {
    }

    /**
     * Read a request HMC sent HMI: a new hearing's or an amendment's {@code {"hearingRequest": {...}}}.
     */
    static Request read(String body) {
        var request = object(object(JsonParser.parseString(body)), "hearingRequest");
        var hearingCase = object(request, "_case");
        var listing = object(request, "listing");

        String venue = null;
        for (var location : array(listing, "listingLocations")) {
            var value = location.getAsJsonObject();
            if ("Court".equalsIgnoreCase(string(value, "locationType"))) {
                venue = string(value, "locationId");
                break;
            }
        }
        if (venue == null) {
            venue = string(object(hearingCase, "caseCourt"), "locationId");
        }

        LocalDateTime earliest = dateTime(string(listing, "listingDate"));
        if (earliest == null) {
            var date = dateTime(string(listing, "listingStartDate"));
            earliest = date == null ? null : date.toLocalDate().atTime(START_OF_DAY);
        }

        String judge = null;
        for (var joh : array(listing, "listingJohs")) {
            var value = joh.getAsJsonObject();
            if (!"EXCLUDE".equalsIgnoreCase(string(value, "listingJohPreference"))) {
                judge = string(value, "listingJohId");
                break;
            }
        }

        var version = number(hearingCase, "caseVersionId");
        var duration = number(listing, "listingDuration");
        return new Request(
            string(hearingCase, "caseIdHMCTS"),
            string(hearingCase, "caseTitle"),
            version == null ? 1 : version,
            venue,
            earliest,
            duration == null ? DEFAULT_DURATION_MINUTES : duration,
            judge
        );
    }

    /**
     * When to list a hearing nobody chose a time for, in court time: the first working day the request allows, but
     * not before the next working day, at the time the request asks for or else the start of the court day.
     *
     * @param today today's date in court time
     */
    static LocalDateTime defaultStart(Request request, LocalDate today) {
        var nextWorkingDay = workingDayFrom(today.plusDays(1));
        var earliest = request.earliest();
        if (earliest == null || earliest.toLocalDate().isBefore(nextWorkingDay)) {
            return nextWorkingDay.atTime(START_OF_DAY);
        }
        return workingDayFrom(earliest.toLocalDate()).atTime(earliest.toLocalTime());
    }

    /**
     * List Assist's listing of the hearing for this request.
     *
     * @param start when the hearing starts, in court time
     * @param now   the time now, in UTC
     */
    static String listing(String hearingId, Request request, LocalDateTime start, LocalDateTime now) {
        var hearing = hearing(hearingId, request, now);
        hearing.add("hearingCaseStatus", code(LISTED, "Listed"));
        hearing.add("hearingStatus", code("FIXED", "Fixed"));
        hearing.addProperty("hearingStartTime", utc(start).toString());
        hearing.addProperty("hearingEndTime", utc(start.plusMinutes(request.duration())).toString());
        if (request.venue() != null) {
            var venue = new JsonObject();
            venue.addProperty("locationIdCaseHQ", request.venue());
            venue.addProperty("locationName", "cftlib court " + request.venue());
            var reference = new JsonObject();
            reference.addProperty("key", "EPIMS");
            reference.addProperty("value", request.venue());
            var references = new JsonArray();
            references.add(reference);
            venue.add("locationReferences", references);
            hearing.add("hearingVenue", venue);
        }
        var room = new JsonObject();
        room.addProperty("locationIdCaseHQ", "1");
        room.addProperty("locationName", "Room 1");
        hearing.add("hearingRoom", room);
        if (request.judge() != null) {
            var judge = new JsonObject();
            judge.addProperty("johId", request.judge());
            judge.addProperty("isPresiding", true);
            var johs = new JsonArray();
            johs.add(judge);
            hearing.add("hearingJohs", johs);
        }
        return response(hearingId, hearing, now);
    }

    /**
     * List Assist closing the hearing, as it does once it has cancelled a hearing HMC asked it to cancel.
     *
     * @param reason the cancellation reason HMC sent, if any
     * @param now    the time now, in UTC
     */
    static String cancellation(String hearingId, Request request, String reason, LocalDateTime now) {
        var hearing = hearing(hearingId, request, now);
        hearing.add("hearingCaseStatus", code(CLOSED, "Closed"));
        hearing.add("hearingStatus", code("CNCL", "Cancelled"));
        if (reason != null) {
            hearing.addProperty("hearingCancellationReason", reason.length() > 70 ? reason.substring(0, 70) : reason);
        }
        return response(hearingId, hearing, now);
    }

    /**
     * The cancellation reason in a request HMC sent HMI to cancel a hearing: {@code {"_cancel": {...}}}.
     */
    static String cancellationReason(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        return string(object(object(JsonParser.parseString(body)), "_cancel"), "cancellationReasonCode");
    }

    /**
     * List Assist reporting that it cannot list the hearing, which HMC puts in EXCEPTION.
     */
    static String exception(int errorCode, String description) {
        var error = new JsonObject();
        error.addProperty("errCode", errorCode);
        error.addProperty("errDesc", description);
        var body = new JsonObject();
        body.add("err", error);
        return body.toString();
    }

    private static JsonObject hearing(String hearingId, Request request, LocalDateTime now) {
        var hearing = new JsonObject();
        hearing.addProperty("listingRequestId", hearingId);
        hearing.addProperty("hearingCaseVersionId", request.version());
        hearing.addProperty("hearingCaseIdHMCTS", request.caseReference());
        hearing.addProperty("hearingIdCaseHQ", "cftlib-" + hearingId);
        hearing.addProperty("hearingPrivate", false);
        hearing.addProperty("hearingRisk", false);
        hearing.addProperty("hearingTranslatorRequired", false);
        hearing.addProperty("hearingCreatedDate", now.toString());
        hearing.addProperty("hearingCreatedBy", "cftlib");
        return hearing;
    }

    private static String response(String hearingId, JsonObject hearing, LocalDateTime now) {
        var meta = new JsonObject();
        meta.addProperty("timestamp", now.toString());
        meta.addProperty("transactionIdCaseHQ", "cftlib-" + hearingId);
        var hearingResponse = new JsonObject();
        hearingResponse.add("meta", meta);
        hearingResponse.add("hearing", hearing);
        var body = new JsonObject();
        body.add("hearingResponse", hearingResponse);
        return body.toString();
    }

    private static JsonObject code(String code, String description) {
        var value = new JsonObject();
        value.addProperty("code", code);
        value.addProperty("description", description);
        return value;
    }

    private static LocalDate workingDayFrom(LocalDate date) {
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * A court time in UTC, as HMC keeps it.
     */
    static LocalDateTime utc(LocalDateTime courtTime) {
        return courtTime.atZone(COURT_TIME).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // HMC sends HMI dates as 2026-11-20T00:00:00Z and date-times as 2026-11-20T10:00:00Z, both in UTC. Returned in
    // court time.
    private static LocalDateTime dateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            OffsetDateTime instant;
            try {
                instant = OffsetDateTime.parse(value);
            } catch (DateTimeParseException e) {
                instant = LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
            }
            return instant.atZoneSameInstant(COURT_TIME).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // A whole number, or null when it's missing, null or isn't one.
    private static Integer number(JsonObject parent, String name) {
        var value = parent.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject object(JsonObject parent, String name) {
        return object(parent.get(name));
    }

    private static JsonArray array(JsonObject parent, String name) {
        var value = parent.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String name) {
        var value = parent.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
