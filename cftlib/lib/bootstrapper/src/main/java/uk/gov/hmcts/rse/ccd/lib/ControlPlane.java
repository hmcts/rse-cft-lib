package uk.gov.hmcts.rse.ccd.lib;

import java.util.concurrent.CountDownLatch;

import lombok.SneakyThrows;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

public class ControlPlane {
    private static final CountDownLatch DB_READY = new CountDownLatch(1);
    private static final CountDownLatch ES_READY = new CountDownLatch(1);
    private static final CountDownLatch AUTH_READY = new CountDownLatch(1);
    // Ready straight away unless the servicebus feature is on.
    private static final CountDownLatch SERVICEBUS_READY = new CountDownLatch(1);
    // Used to wait for all services to be ready; LibRunner sets the count to the apps it launches.
    private static volatile CountDownLatch APPS_READY = new CountDownLatch(Project.values().length);
    // Wait for the API to be provided from the runtime
    private static final CountDownLatch API_READY = new CountDownLatch(1);
    private static volatile Throwable INIT_EXCEPTION;
    private static volatile CFTLib api;

    @SneakyThrows
    public static void waitForDB() {
        DB_READY.await();
        if (INIT_EXCEPTION != null) {
            throw INIT_EXCEPTION;
        }
    }

    @SneakyThrows
    public static void waitForES() {
        ES_READY.await();
        if (INIT_EXCEPTION != null) {
            throw INIT_EXCEPTION;
        }
    }

    @SneakyThrows
    public static void waitForServiceBus() {
        SERVICEBUS_READY.await();
        if (INIT_EXCEPTION != null) {
            throw INIT_EXCEPTION;
        }
    }

    public static void setServiceBusReady() {
        SERVICEBUS_READY.countDown();
    }

    public static void setServiceBusError(Throwable t) {
        INIT_EXCEPTION = t;
        SERVICEBUS_READY.countDown();
    }

    public static void setDBReady() {
        DB_READY.countDown();
    }

    public static void setESReady() {
        ES_READY.countDown();
    }

    public static void setDBError(Throwable t) {
        INIT_EXCEPTION = t;
        DB_READY.countDown();
    }

    public static void setESError(Throwable t) {
        INIT_EXCEPTION = t;
        ES_READY.countDown();
    }

    private static volatile boolean booted;

    @SneakyThrows
    public static synchronized void waitForBoot() {
        APPS_READY.await();
        // Services and tests that use Service Bus may send as soon as cftlib reports it is up.
        waitForServiceBus();
        if (!booted) {
            // One-off global search index creation
            API_READY.await();
            api.createGlobalSearchIndex();
            booted = true;
        }
    }

    @SneakyThrows
    public static void waitForAuthServer() {
        AUTH_READY.await();
    }

    // The number of applications LibRunner launches, which depends on the optional features a build turns on.
    public static void expectApps(int count) {
        APPS_READY = new CountDownLatch(count);
    }

    // Signal that an application has booted.
    public static void appReady(String name) {
        APPS_READY.countDown();
        var c = APPS_READY.getCount();
        System.out.println("**** Cftlib application " + name + " is ready **** " + c + " remaining");
    }

    @SneakyThrows
    public static CFTLib getApi() {
        waitForBoot();
        return api;
    }

    public static void setApi(CFTLib api) {
        ControlPlane.api = api;
        API_READY.countDown();
    }

    public static void setAuthReady() {
        AUTH_READY.countDown();
    }

    public static String getEnvVar(String var, Object defaultIfNull) {
        var v = System.getenv(var);
        return v == null
            ? defaultIfNull.toString()
            : v;
    }

    private static volatile Runnable shutdownTask = () -> {
    };

    /**
     * Registers work to run before the JVM is halted, for dependencies cftlib started outside it:
     * halting runs no shutdown hooks, and this is the ordinary way a cftlib boot ends.
     */
    public static void setShutdownTask(Runnable task) {
        shutdownTask = task;
    }

    /**
     * Halts the JVM, having shut down the dependencies cftlib started outside it. Halting is how a
     * cftlib run ends, deliberately on the build-time paths and via {@link #failFast} on an error,
     * and it runs no shutdown hooks.
     */
    public static void shutdown(int status) {
        try {
            shutdownTask.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        Runtime.getRuntime().halt(status);
    }

    // Immediately terminate upon an unhandled error that has caused a service to terminate.
    // This will ensure the JVM terminates even if we've started other threads.
    public static final Thread.UncaughtExceptionHandler failFast = (thread, exception) -> {
        exception.printStackTrace();
        try {
            shutdownTask.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("*** Cftlib thread " + thread.getName() + " terminated with an unhandled exception ***");
        System.out.println("Logs are available in build/cftlib/logs");
        System.out.println("For further support visit https://moj.enterprise.slack.com/archives/C033F1GDD6Z");
        Runtime.getRuntime().halt(-1);
    };
}
