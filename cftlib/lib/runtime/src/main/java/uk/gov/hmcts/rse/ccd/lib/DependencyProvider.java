package uk.gov.hmcts.rse.ccd.lib;

/**
 * Brings up the dependencies cftlib does not run in-process.
 */
public interface DependencyProvider {

    /**
     * Start every dependency this provider owns, blocking until they have been launched.
     * Readiness is signalled separately by {@link Readiness}.
     */
    /**
     * Bring up anything whose startup destroys or replaces state the readiness probes can see,
     * before monitoring begins. Returns when that state is settled.
     */
    default void prepare() {
    }

    void start();

    /**
     * Shut down anything this provider started outside the JVM. Idempotent.
     */
    default void stop() {
    }
}
