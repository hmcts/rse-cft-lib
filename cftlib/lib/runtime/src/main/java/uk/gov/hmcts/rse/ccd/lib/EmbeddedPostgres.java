package uk.gov.hmcts.rse.ccd.lib;

/**
 * Runs postgres from the binaries published by io.zonky.test, with no container runtime.
 */
public class EmbeddedPostgres implements ManagedDependency {

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("Embedded postgres is not implemented yet.");
    }
}
