package uk.gov.hmcts.rse;

/**
 * How cftlib provides the dependencies it does not run in its own JVM.
 */
public enum DependencyMode {
    /**
     * Postgres, elasticsearch and XUI run as containers via docker compose.
     */
    Docker,

    /**
     * Postgres, elasticsearch and XUI run on the host without a container runtime.
     */
    Process
}
