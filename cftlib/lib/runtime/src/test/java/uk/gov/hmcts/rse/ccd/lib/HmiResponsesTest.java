package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class HmiResponsesTest {

    // A request as hmc-hmi-outbound-adapter sends it, trimmed to the fields List Assist's response depends on.
    private static final String REQUEST = """
        {
          "hearingRequest": {
            "_case": {
              "caseIdHMCTS": "1700000000000001",
              "caseListingRequestId": "2000000123",
              "caseTitle": "Joe Bloggs v Jane Doe",
              "caseVersionId": 3,
              "caseCourt": {"locationType": "Court", "locationId": "111111", "locationReferenceType": "EPIMS"}
            },
            "listing": {
              "listingStartDate": "2026-12-07T00:00:00Z",
              "listingEndDate": "2026-12-31T00:00:00Z",
              "listingDuration": 90,
              "listingLocations": [
                {"locationType": "Region", "locationId": "2", "locationReferenceType": "CASEHQ"},
                {"locationType": "Court", "locationId": "20262", "locationReferenceType": "EPIMS"}
              ],
              "listingJohs": [
                {"listingJohId": "4917924", "listingJohPreference": "EXCLUDE"},
                {"listingJohId": "4925644", "listingJohPreference": "MUSTINC"}
              ]
            }
          }
        }
        """;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 12, 0);

    @Test
    void readsWhatToListFromHmcsRequest() {
        var request = HmiResponses.read(REQUEST);

        assertThat(request.caseReference()).isEqualTo("1700000000000001");
        assertThat(request.caseTitle()).isEqualTo("Joe Bloggs v Jane Doe");
        assertThat(request.version()).isEqualTo(3);
        assertThat(request.venue()).isEqualTo("20262");
        assertThat(request.earliest()).isEqualTo(LocalDateTime.of(2026, 12, 7, 10, 0));
        assertThat(request.duration()).isEqualTo(90);
        assertThat(request.judge()).isEqualTo("4925644");
    }

    @Test
    void fallsBackToTheCasesCourtAndAnHourWhenTheRequestDoesNotSay() {
        var request = HmiResponses.read("""
            {"hearingRequest": {"_case": {"caseIdHMCTS": "1", "caseVersionId": 1,
              "caseCourt": {"locationType": "Court", "locationId": "111111"}}, "listing": {}}}
            """);

        assertThat(request.venue()).isEqualTo("111111");
        assertThat(request.duration()).isEqualTo(60);
        assertThat(request.earliest()).isNull();
        assertThat(request.judge()).isNull();
    }

    @Test
    void treatsNullNumbersAsMissing() {
        var request = HmiResponses.read("""
            {"hearingRequest": {"_case": {"caseIdHMCTS": "1", "caseVersionId": null},
              "listing": {"listingDuration": null}}}
            """);

        assertThat(request.version()).isEqualTo(1);
        assertThat(request.duration()).isEqualTo(60);
    }

    @Test
    void takesAFixedDateTheRequestGives() {
        var request = HmiResponses.read(REQUEST.replace(
            "\"listingDuration\"", "\"listingDate\": \"2026-12-09T14:30:00Z\", \"listingDuration\""));

        assertThat(request.earliest()).isEqualTo(LocalDateTime.of(2026, 12, 9, 14, 30));
    }

    @Test
    void readsHmcsUtcTimesAsUkTime() {
        var request = HmiResponses.read(REQUEST.replace(
            "\"listingDuration\"", "\"listingDate\": \"2026-07-09T13:30:00Z\", \"listingDuration\""));

        assertThat(request.earliest()).isEqualTo(LocalDateTime.of(2026, 7, 9, 14, 30));
    }

    @Test
    void listsAtTheStartOfTheWindowUnlessItHasPassed() {
        var request = HmiResponses.read(REQUEST);

        assertThat(HmiResponses.defaultStart(request, LocalDate.of(2026, 11, 19)))
            .isEqualTo(LocalDateTime.of(2026, 12, 7, 10, 0));
        // Friday: the window opened on the 7th, so the next working day is Monday.
        assertThat(HmiResponses.defaultStart(request, LocalDate.of(2026, 12, 11)))
            .isEqualTo(LocalDateTime.of(2026, 12, 14, 10, 0));
    }

    @Test
    void movesAWindowOpeningAtTheWeekendToMonday() {
        // Saturday 5 December.
        var request = HmiResponses.read(REQUEST.replace("2026-12-07T00:00:00Z", "2026-12-05T00:00:00Z"));

        assertThat(HmiResponses.defaultStart(request, LocalDate.of(2026, 11, 19)))
            .isEqualTo(LocalDateTime.of(2026, 12, 7, 10, 0));
    }

    @Test
    void keepsTheTimeOfAFixedDateMovedOffTheWeekend() {
        // Sunday 6 December.
        var request = HmiResponses.read(REQUEST.replace(
            "\"listingDuration\"", "\"listingDate\": \"2026-12-06T14:30:00Z\", \"listingDuration\""));

        assertThat(HmiResponses.defaultStart(request, LocalDate.of(2026, 11, 19)))
            .isEqualTo(LocalDateTime.of(2026, 12, 7, 14, 30));
    }

    @Test
    void listsTheHearingAsListAssistWould() {
        var request = HmiResponses.read(REQUEST);
        var start = LocalDateTime.of(2026, 12, 7, 10, 0);

        var hearing = hearing(HmiResponses.listing("2000000123", request, start, NOW));

        assertThat(hearing.get("hearingCaseVersionId").getAsInt()).isEqualTo(3);
        assertThat(hearing.get("hearingCaseIdHMCTS").getAsString()).isEqualTo("1700000000000001");
        assertThat(hearing.getAsJsonObject("hearingCaseStatus").get("code").getAsString()).isEqualTo("100");
        assertThat(hearing.getAsJsonObject("hearingStatus").get("code").getAsString()).isEqualTo("FIXED");
        assertThat(hearing.get("hearingStartTime").getAsString()).isEqualTo("2026-12-07T10:00");
        assertThat(hearing.get("hearingEndTime").getAsString()).isEqualTo("2026-12-07T11:30");
        var venue = hearing.getAsJsonObject("hearingVenue");
        assertThat(venue.get("locationIdCaseHQ").getAsString()).isEqualTo("20262");
        assertThat(venue.getAsJsonArray("locationReferences").get(0).getAsJsonObject().get("key").getAsString())
            .isEqualTo("EPIMS");
        assertThat(hearing.getAsJsonArray("hearingJohs").get(0).getAsJsonObject().get("johId").getAsString())
            .isEqualTo("4925644");
    }

    @Test
    void sendsHmcTheListingInUtc() {
        var request = HmiResponses.read(REQUEST);
        var start = LocalDateTime.of(2027, 7, 6, 10, 0);

        var hearing = hearing(HmiResponses.listing("2000000123", request, start, NOW));

        assertThat(hearing.get("hearingStartTime").getAsString()).isEqualTo("2027-07-06T09:00");
        assertThat(hearing.get("hearingEndTime").getAsString()).isEqualTo("2027-07-06T10:30");
    }

    @Test
    void closesACancelledHearingWithHmcsReason() {
        var request = HmiResponses.read(REQUEST);
        var reason = HmiResponses.cancellationReason("{\"_cancel\": {\"cancellationReasonCode\": \"withdraw\"}}");

        var hearing = hearing(HmiResponses.cancellation("2000000123", request, reason, NOW));

        assertThat(hearing.getAsJsonObject("hearingCaseStatus").get("code").getAsString()).isEqualTo("8");
        assertThat(hearing.getAsJsonObject("hearingStatus").get("code").getAsString()).isEqualTo("CNCL");
        assertThat(hearing.get("hearingCancellationReason").getAsString()).isEqualTo("withdraw");
        assertThat(hearing.get("hearingCaseVersionId").getAsInt()).isEqualTo(3);
    }

    @Test
    void reportsAHearingItCannotList() {
        var error = JsonParser.parseString(HmiResponses.exception(2000, "No room")).getAsJsonObject()
            .getAsJsonObject("err");

        assertThat(error.get("errCode").getAsInt()).isEqualTo(2000);
        assertThat(error.get("errDesc").getAsString()).isEqualTo("No room");
    }

    private static JsonObject hearing(String response) {
        var body = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("hearingResponse");
        assertThat(body.getAsJsonObject("meta").get("transactionIdCaseHQ").getAsString()).isNotBlank();
        return body.getAsJsonObject("hearing");
    }
}
