package uk.gov.hmcts.rse.ccd.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.SneakyThrows;

/**
 * Runs cftlib's dependencies as host processes or in-process components instead of containers.
 */
public class ProcessProvider implements DependencyProvider {

    private static final long INTERRUPT_WAIT_MILLIS = 10_000;

    /**
     * What this provider has taken responsibility for. A dependency joins before it is started,
     * not after: starting XUI takes minutes, a failure anywhere else in the boot halts the JVM
     * without running shutdown hooks, and {@link #stop()} is the only thing that would have
     * stopped the install it was part way through.
     */
    private final List<ManagedDependency> owned = new CopyOnWriteArrayList<>();

    /** The threads starting those dependencies, which stopping has to interrupt. */
    private final List<Thread> starting = new CopyOnWriteArrayList<>();

    /**
     * Brings the database up, which a clean boot does by stopping the last one and deleting its
     * data directory. Runs before anything observes the port, and so before monitoring starts.
     */
    @Override
    @SneakyThrows
    public void prepare() {
        for (var dependency : resolve()) {
            if (dependency instanceof EmbeddedPostgres) {
                System.out.println("**** Cftlib starting " + dependency.name());
                owned.add(dependency);
                try {
                    dependency.start();
                } catch (Throwable e) {
                    throw new RuntimeException("Failed to start " + dependency.name(), e);
                }
            }
        }
    }

    @Override
    @SneakyThrows
    public void start() {
        var dependencies = new ArrayList<>(resolve());
        // Already up, in prepare().
        dependencies.removeIf(EmbeddedPostgres.class::isInstance);

        // The rest are independent of one another, and building XUI is slow,
        // so bring them up concurrently.
        var failures = new CopyOnWriteArrayList<Throwable>();
        var threads = new ArrayList<Thread>();
        for (var dependency : dependencies) {
            owned.add(dependency);
            var t = new Thread(() -> {
                try {
                    System.out.println("**** Cftlib starting " + dependency.name());
                    dependency.start();
                } catch (Throwable e) {
                    failures.add(new RuntimeException("Failed to start " + dependency.name(), e));
                }
            });
            t.setName("cftlib-" + dependency.name());
            t.setDaemon(true);
            t.start();
            threads.add(t);
            starting.add(t);
        }

        // Reported as soon as any dependency fails rather than once they have all finished:
        // building XUI takes minutes, and waiting for it would hide a database that failed
        // in seconds behind a readiness timeout.
        while (failures.isEmpty() && threads.stream().anyMatch(Thread::isAlive)) {
            for (var t : threads) {
                t.join(100);
                if (!failures.isEmpty()) {
                    break;
                }
            }
        }
        if (!failures.isEmpty()) {
            stop();
            throw failures.get(0);
        }
    }

    @Override
    public void stop() {
        // The boot is over, so a dependency part way through starting has to be taken with it.
        // Interrupting is what reaches the child process: zt-exec destroys it when the thread
        // waiting on it is interrupted, and its own destroyOnExit hook cannot help here, since
        // a boot that failed ends by halting and that runs no shutdown hooks.
        for (var t : starting) {
            t.interrupt();
        }
        // Waited for, for the same reason: an install that has not yet seen the interrupt would
        // otherwise carry on without us.
        for (var t : starting) {
            try {
                t.join(INTERRUPT_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        for (var dependency : owned) {
            try {
                dependency.stop();
            } catch (Exception e) {
                System.out.println("**** Cftlib failed to stop " + dependency.name() + ": " + e.getMessage());
            }
        }
    }

    List<ManagedDependency> resolve() {
        var result = new ArrayList<ManagedDependency>();

        switch (CftlibConfig.dbMode()) {
            case EMBEDDED -> {
                // initdb creates the postgres superuser and no other, so a boot configured for a
                // different one would fail every connection and sit out the readiness timeout.
                if (!"postgres".equals(CftlibConfig.dbUsername())) {
                    throw new UnsupportedOperationException("RSE_LIB_DB_USERNAME="
                        + CftlibConfig.dbUsername() + " cannot be used with the embedded database, which "
                        + "only has the postgres superuser. Set RSE_LIB_DB=external to use a database "
                        + "that has that role, or leave the username unset.");
                }
                result.add(new EmbeddedPostgres());
            }
            case EXTERNAL -> {
                // Nothing to start: the database is already running elsewhere.
            }
            case DOCKER -> throw mixingNotSupported("RSE_LIB_DB");
            default -> throw new IllegalStateException();
        }

        switch (CftlibConfig.esMode()) {
            case STUB -> result.add(new EsStub());
            case EXTERNAL -> {
                // Nothing to start: elasticsearch is already running elsewhere.
            }
            case DOCKER -> throw mixingNotSupported("RSE_LIB_ES");
            default -> throw new IllegalStateException();
        }

        switch (CftlibConfig.xuiMode()) {
            case PROCESS -> result.add(Xui.manageCases());
            case OFF -> {
                // XUI is not wanted, eg. when running cftlibTest.
            }
            case DOCKER -> throw mixingNotSupported("RSE_LIB_XUI");
            default -> throw new IllegalStateException();
        }

        switch (CftlibConfig.xuiManageOrgMode()) {
            case PROCESS -> result.add(Xui.manageOrg());
            case OFF -> {
                // Manage organisations is off by default outside docker mode.
            }
            case DOCKER -> throw mixingNotSupported("RSE_LIB_XUI_MO");
            default -> throw new IllegalStateException();
        }

        return result;
    }

    private UnsupportedOperationException mixingNotSupported(String var) {
        return new UnsupportedOperationException(
            var + "=docker cannot be combined with RSE_LIB_DEPENDENCY_MODE=process. "
                + "Run everything in docker, or set " + var + " to a non-docker value.");
    }
}
