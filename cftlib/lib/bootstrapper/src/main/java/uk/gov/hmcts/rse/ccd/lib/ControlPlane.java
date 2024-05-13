package uk.gov.hmcts.rse.ccd.lib;

import java.util.concurrent.CountDownLatch;

import lombok.SneakyThrows;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

public class ControlPlane {
    private static final CountDownLatch DB_READY = new CountDownLatch(1);
    private static final CountDownLatch ES_READY = new CountDownLatch(1);
    private static final CountDownLatch AUTH_READY = new CountDownLatch(1);
    // Used to wait for all services to be ready
    private static final CountDownLatch APPS_READY = new CountDownLatch(Project.values().length);
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

    @SneakyThrows
    public static void waitForBoot() {
        APPS_READY.await();
    }

    @SneakyThrows
    public static void waitForAuthServer() {
        AUTH_READY.await();
    }

    // Signal that an application has booted.
    public static void appReady() {
        APPS_READY.countDown();
    }

    @SneakyThrows
    public static CFTLib getApi() {
        waitForBoot();
        API_READY.await();
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

    // Immediately terminate upon an unhandled error that has caused a service to terminate.
    // This will ensure the JVM terminates even if we've started other threads.
    public static final Thread.UncaughtExceptionHandler failFast = (thread, exception) -> {
        exception.printStackTrace();
        System.out.println("*** Cftlib thread " + thread.getName() + " terminated with an unhandled exception ***");
        System.out.println("Logs are available in build/cftlib/logs");
        System.out.println("For further support visit https://moj.enterprise.slack.com/archives/C033F1GDD6Z");
        Runtime.getRuntime().halt(-1);
    };
}
