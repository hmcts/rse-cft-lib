package uk.gov.hmcts.rse.ccd.lib;

/**
 * A single dependency cftlib runs itself, rather than delegating to docker compose.
 */
public interface ManagedDependency {

    String name();

    /**
     * Start the dependency, adopting an already running instance where one is healthy.
     */
    void start() throws Exception;

    default void stop() {
    }
}
