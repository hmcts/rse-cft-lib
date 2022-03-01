package uk.gov.hmcts.rse.ccd.lib;

import java.util.concurrent.CountDownLatch;

public class ControlPlane {
    private static volatile Throwable INIT_EXCEPTION;
    private static final CountDownLatch DB_READY = new CountDownLatch(1);
    private static final CountDownLatch ES_READY = new CountDownLatch(1);

    public static void waitForDB() {
        try {
            DB_READY.await();
            if (INIT_EXCEPTION != null) {
                throw INIT_EXCEPTION;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitForES() {
        try {
            ES_READY.await();
            if (INIT_EXCEPTION != null) {
                throw INIT_EXCEPTION;
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
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

    public static void waitForBoot() {

    }
}
