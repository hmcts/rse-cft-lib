package uk.gov.hmcts.rse.ccd.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.SneakyThrows;

/**
 * Runs cftlib's dependencies as host processes or in-process components instead of containers.
 */
public class ProcessProvider implements DependencyProvider {

    private final List<ManagedDependency> started = new CopyOnWriteArrayList<>();

    @Override
    @SneakyThrows
    public void start() {
        var dependencies = new ArrayList<>(resolve());

        // Postgres goes first and alone. A clean boot stops the server from the last boot and
        // deletes its data directory, and the readiness monitor is already polling that port: if
        // it connected in between it would create the databases and open the gates against a
        // server being torn down, and every service would then migrate into it.
        for (var dependency : List.copyOf(dependencies)) {
            if (dependency instanceof EmbeddedPostgres) {
                dependencies.remove(dependency);
                System.out.println("**** Cftlib starting " + dependency.name());
                try {
                    dependency.start();
                    started.add(dependency);
                } catch (Throwable e) {
                    throw new RuntimeException("Failed to start " + dependency.name(), e);
                }
            }
        }

        // The rest are independent of one another, and building XUI is slow,
        // so bring them up concurrently.
        var failures = new CopyOnWriteArrayList<Throwable>();
        var threads = new ArrayList<Thread>();
        for (var dependency : dependencies) {
            var t = new Thread(() -> {
                try {
                    System.out.println("**** Cftlib starting " + dependency.name());
                    dependency.start();
                    started.add(dependency);
                } catch (Throwable e) {
                    failures.add(new RuntimeException("Failed to start " + dependency.name(), e));
                }
            });
            t.setName("cftlib-" + dependency.name());
            t.setDaemon(true);
            t.start();
            threads.add(t);
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
            // The boot is over, so stop the dependencies still starting rather than leaving a
            // yarn install running unparented once the JVM halts.
            for (var t : threads) {
                t.interrupt();
            }
            for (var dependency : dependencies) {
                try {
                    dependency.stop();
                } catch (Exception e) {
                    System.out.println("**** Cftlib failed to stop " + dependency.name() + ": " + e.getMessage());
                }
            }
            throw failures.get(0);
        }
    }

    @Override
    public void stop() {
        for (var dependency : started) {
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
            case EMBEDDED -> result.add(new EmbeddedPostgres());
            case EXTERNAL -> {
                // Nothing to start: the database is already running elsewhere.
            }
            case DOCKER -> throw mixingNotSupported("RSE_LIB_DB");
            default -> throw new IllegalStateException();
        }

        switch (CftlibConfig.esMode()) {
            case STUB -> result.add(new EsStub());
            case PROCESS -> throw new UnsupportedOperationException(
                "RSE_LIB_ES=process is not implemented yet. Use RSE_LIB_ES=stub, or run elasticsearch "
                    + "yourself and set RSE_LIB_ES=external with SEARCH_ELASTIC_HOSTS.");
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
