package uk.gov.hmcts.rse.ccd.lib;

import java.util.concurrent.CountDownLatch;

import lombok.SneakyThrows;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.impl.Project;

public class ControlPlane {
    private static volatile Throwable INIT_EXCEPTION;
    private static final CountDownLatch DB_READY = new CountDownLatch(1);
    private static final CountDownLatch ES_READY = new CountDownLatch(1);
    // We wait for all services to be ready, except app under test which is coordinated by spring test.
    private static final CountDownLatch APPS_READY = new CountDownLatch(Project.values().length - 1);

    // Wait for the API to be provided from the runtime
    private static final CountDownLatch API_READY = new CountDownLatch(1);

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

    // Signal that an application has booted.
    public static void appReady() {
        APPS_READY.countDown();
    }

    public static void setApi(CFTLib api) {
        ControlPlane.api = api;
        API_READY.countDown();
    }

    @SneakyThrows
    private static CFTLib getApi() {
        API_READY.await();
        return api;
    }
}
