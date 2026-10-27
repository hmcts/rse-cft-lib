package uk.gov.hmcts.rse.ccd.lib;

/**
 * Brings up the dependencies cftlib does not run in-process.
 */
public interface DependencyProvider {

    /**
     * Start every dependency this provider owns, blocking until they have been launched.
     * Readiness is signalled separately by {@link Readiness}.
     */
    void start();
}
