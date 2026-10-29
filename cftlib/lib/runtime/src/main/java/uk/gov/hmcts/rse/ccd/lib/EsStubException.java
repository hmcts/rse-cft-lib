package uk.gov.hmcts.rse.ccd.lib;

/**
 * A failure the stub reports back to its caller as an elasticsearch shaped error.
 *
 * <p>The error types deliberately avoid {@code index_not_found_exception} and
 * {@code no_shard_available_action_exception}: callers treat those as "elasticsearch is still
 * warming up" and retry until they time out, which would hide the real cause.
 */
class EsStubException extends RuntimeException {

    static final String UNSUPPORTED = "cftlib_es_stub_unsupported_clause";
    static final String BAD_REQUEST = "cftlib_es_stub_bad_request";
    static final String QUERY_ERROR = "cftlib_es_stub_query_error";

    private final String type;
    private final int status;

    EsStubException(String type, int status, String reason) {
        super(reason);
        this.type = type;
        this.status = status;
    }

    static EsStubException unsupported(String clause, String detail) {
        return new EsStubException(UNSUPPORTED, 400,
            "The cftlib elasticsearch stub does not support '" + clause + "'. " + detail
                + " The stub answers searches from postgres and implements only the subset of the "
                + "elasticsearch query language that CCD issues. Run with RSE_LIB_ES=external against a "
                + "real elasticsearch if you need this clause.");
    }

    static EsStubException badRequest(String reason) {
        return new EsStubException(BAD_REQUEST, 400, "The cftlib elasticsearch stub rejected a request: " + reason);
    }

    String type() {
        return type;
    }

    int status() {
        return status;
    }
}
