package uk.gov.hmcts.ccd.sdk.api;

import lombok.Builder;
import lombok.Value;

public record EventPayload<T, S>(long caseReference, T payload) {
}
