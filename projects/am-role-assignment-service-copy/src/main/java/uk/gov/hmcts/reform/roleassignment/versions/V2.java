package uk.gov.hmcts.reform.roleassignment.versions;


public final class V2 {

    private V2() {
    }

    public static final class MediaType {
        private MediaType() {
        }

        // External API
        public static final String SERVICE = "application/vnd.uk.gov.hmcts.role-assignment-service";
        public static final String POST_ASSIGNMENTS = SERVICE
            + ".post-assignment-query-request+json;charset=UTF-8;version=2.0";
    }


}

