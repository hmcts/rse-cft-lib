package uk.gov.hmcts.rse.ccd.lib;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;

/**
 * Starts cftlib's dependencies and opens the control plane gates as they become ready.
 */
public class DependencyBoot {

    private final Readiness readiness = new Readiness();

    public void startBoot() {
        try {
            var provider = provider();
            // Start our monitoring of the dependencies before we start them, since starting
            // can take a few seconds and we don't want to block boot if they are already running.
            ControlPlane.setApi(new CFTLibApiImpl());
            if (null != System.getenv("CI") || null != System.getenv("RSE_LIB_CLEAN_BOOT")) {
                // On CNP jenkins it is possible for daemonised containers to be left running
                // in between builds.
                // If we're running in CI we therefore re-create any existing containers
                // and cannot start monitoring them until after this restart.
                provider.start();
                new Thread(this::monitorStartingServices).start();
            } else {
                // If not in CI boot as fast as possible by immediately checking to
                // see if our dependencies are already up.
                new Thread(this::monitorStartingServices).start();
                provider.start();
            }
        } catch (Exception e) {
            ControlPlane.setDBError(e);
            ControlPlane.setESError(e);
        }
    }

    private DependencyProvider provider() {
        return switch (CftlibConfig.dependencyMode()) {
            case DOCKER -> new ComposeRunner();
            case PROCESS -> new ProcessProvider();
        };
    }

    private void monitorStartingServices() {
        Awaitility.await()
            .pollInSameThread()
            .pollInterval(Duration.ofMillis(100))
            .pollDelay(Duration.ZERO)
            .ignoreExceptions()
            .timeout(10, TimeUnit.MINUTES)
            .until(readiness::authReady);

        ControlPlane.setAuthReady();

        Awaitility.await()
            .pollInSameThread()
            .pollInterval(Duration.ofMillis(100))
            .pollDelay(Duration.ZERO)
            .ignoreExceptions()
            .timeout(10, TimeUnit.MINUTES)
            .until(readiness::dbReady);

        ControlPlane.setDBReady();

        // Wait for elasticsearch
        Awaitility.await()
            .pollInSameThread()
            .pollInterval(Duration.ofMillis(250))
            .pollDelay(Duration.ZERO)
            .ignoreExceptions()
            .timeout(10, TimeUnit.MINUTES)
            .until(readiness::esReady);

        ControlPlane.setESReady();
    }
}
