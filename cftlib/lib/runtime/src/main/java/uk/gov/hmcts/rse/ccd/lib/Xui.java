package uk.gov.hmcts.rse.ccd.lib;

/**
 * Builds and runs an XUI app from a shallow clone using the host's node, with no container runtime.
 */
public class Xui implements ManagedDependency {

    private final String name;

    private Xui(String name) {
        this.name = name;
    }

    public static Xui manageCases() {
        return new Xui("xui-manage-cases");
    }

    public static Xui manageOrg() {
        return new Xui("xui-manage-org");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("Running " + name + " outside docker is not implemented yet.");
    }
}
