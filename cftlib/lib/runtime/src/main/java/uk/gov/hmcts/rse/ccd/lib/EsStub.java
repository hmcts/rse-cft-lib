package uk.gov.hmcts.rse.ccd.lib;

/**
 * Serves the subset of the elasticsearch API that CCD uses, querying case data from postgres
 * instead of maintaining an index.
 */
public class EsStub implements ManagedDependency {

    @Override
    public String name() {
        return "elasticsearch-stub";
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("The elasticsearch stub is not implemented yet.");
    }
}
