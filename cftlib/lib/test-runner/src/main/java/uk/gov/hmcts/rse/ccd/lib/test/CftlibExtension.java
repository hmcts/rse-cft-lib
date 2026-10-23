package uk.gov.hmcts.rse.ccd.lib.test;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstancePreConstructCallback;
import uk.gov.hmcts.rse.ccd.lib.LibRunner;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Start before Spring prepares a PER_CLASS test instance, which happens before BeforeAll callbacks.
 */
public class CftlibExtension implements TestInstancePreConstructCallback {
    @Override
    public void preConstructTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext context) {
        String manifests = System.getProperty("rse.lib.manifests");
        if (manifests == null || manifests.isBlank()) {
            throw new IllegalStateException(
                "CFTLib service manifests are missing. Run tests with the cftlibTest task.");
        }
        LibRunner.startServices(manifests.split(Pattern.quote(File.pathSeparator)));
    }
}
