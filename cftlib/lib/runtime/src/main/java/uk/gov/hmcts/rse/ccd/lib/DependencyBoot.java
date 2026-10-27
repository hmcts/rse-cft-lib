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
            // Halting runs no shutdown hooks, and failing fast on an unhandled error is the
            // ordinary way a boot ends, so teardown is registered with the control plane too.
            ControlPlane.setShutdownTask(provider::stop);
            Runtime.getRuntime().addShutdownHook(new Thread(provider::stop, "cftlib-stop-dependencies"));
            // Start our monitoring of the dependencies before we start them, since starting
            // can take a few seconds and we don't want to block boot if they are already running.
            ControlPlane.setApi(new CFTLibApiImpl());
            var recreatingContainers = provider instanceof ComposeRunner
                && (null != System.getenv("CI") || null != System.getenv("RSE_LIB_CLEAN_BOOT"));
            if (recreatingContainers) {
                // On CNP jenkins it is possible for daemonised containers to be left running
                // in between builds.
                // If we're running in CI we therefore re-create any existing containers
                // and cannot start monitoring them until after this restart.
                provider.start();
                new Thread(this::monitorStartingServices).start();
            } else {
                // Otherwise boot as fast as possible by immediately checking to
                // see if our dependencies are already up.
                new Thread(this::monitorStartingServices).start();
                provider.start();
            }
        } catch (Throwable e) {
            // A dependency that cannot start is fatal, and the control plane gates cannot carry
            // that: they may already be open, and a failure in any dependency would be reported
            // as a database or search error whatever it actually was.
            ControlPlane.failFast.uncaughtException(Thread.currentThread(), e);
        }
    }

    private DependencyProvider provider() {
        return switch (CftlibConfig.dependencyMode()) {
            case DOCKER -> dockerProvider();
            case PROCESS -> new ProcessProvider();
        };
    }

    /**
     * Compose starts the containers whatever the individual dependencies are set to, and other
     * parts of the boot do honour those settings, so a value compose cannot deliver has to be
     * rejected rather than half applied.
     */
    private DependencyProvider dockerProvider() {
        requireDocker("RSE_LIB_DB", CftlibConfig.dbMode(), CftlibConfig.DbMode.DOCKER);
        requireDocker("RSE_LIB_ES", CftlibConfig.esMode(), CftlibConfig.EsMode.DOCKER);
        // XUI can be turned off in docker mode, since its containers sit behind a compose profile.
        if (CftlibConfig.xuiMode() == CftlibConfig.XuiMode.PROCESS) {
            throw unsupported("RSE_LIB_XUI", "process");
        }
        if (CftlibConfig.xuiManageOrgMode() == CftlibConfig.XuiMode.PROCESS) {
            throw unsupported("RSE_LIB_XUI_MO", "process");
        }
        return new ComposeRunner();
    }

    private <T extends Enum<T>> void requireDocker(String var, T configured, T docker) {
        if (configured != docker) {
            throw unsupported(var, configured.name().toLowerCase());
        }
    }

    private IllegalStateException unsupported(String var, String value) {
        return new IllegalStateException(var + "=" + value + " cannot be combined with "
            + "RSE_LIB_DEPENDENCY_MODE=docker, which runs every dependency as a container. Set "
            + "RSE_LIB_DEPENDENCY_MODE=process to choose dependencies individually.");
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
