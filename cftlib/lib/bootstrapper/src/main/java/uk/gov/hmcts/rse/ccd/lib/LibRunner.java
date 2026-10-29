package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.System.getenv;

import lombok.SneakyThrows;

public class LibRunner {
    // The idam simulator provides the auth server the CFT services wait for, so it is started
    // alongside the runtime rather than behind the auth gate.
    private static final String AUTH_SERVER_MANIFEST = "rse-idam-simulator";
    private static final int AUTH_SERVER_PORT = 5062;
    private static final int AUTH_SERVER_PORT_WAIT_SECONDS = 60;

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("**** cftlib bootstrap");
        Thread.currentThread().setUncaughtExceptionHandler(ControlPlane.failFast);
        doRun(args);
    }

    @SneakyThrows
    private static void doRun(String[] args) {
        setConfigProperties();
        // Every manifest after the runtime is a Spring Boot application that reports when it is ready, apart from the
        // idam simulator, which runs without the cftlib agent.
        ControlPlane.expectApps((int) Arrays.stream(args).skip(1)
            .filter(f -> !new File(f).getName().equals(AUTH_SERVER_MANIFEST))
            .count());
        var threads = new ArrayList<Thread>();
        {
            var runtime = args[0];
            var t = new Thread(() -> launchAppOrFailFast(new File(runtime)));
            t.setName("runtime");
            t.start();
            threads.add(t);
        }

        var rest = new ArrayList<String>();
        for (var f : Arrays.asList(args).subList(1, args.length)) {
            if (new File(f).getName().equals(AUTH_SERVER_MANIFEST)) {
                var t = new Thread(() -> {
                    awaitAuthServerPort();
                    launchAppOrFailFast(new File(f));
                });
                // launchAppOrFailFast installs this itself, but the port check runs before it and
                // its failure is fatal: without a handler the thread would die quietly and the
                // services would wait on an auth server that is never coming.
                t.setUncaughtExceptionHandler(ControlPlane.failFast);
                t.setName(AUTH_SERVER_MANIFEST);
                t.start();
                threads.add(t);
            } else {
                rest.add(f);
            }
        }

        // Cannot start spring boot apps until Oauth server ready.
        ControlPlane.waitForAuthServer();

        rest.forEach(f -> {
            var t = new Thread(() -> launchAppOrFailFast(new File(f)));
            t.start();
            threads.add(t);
        });
        for (Thread thread : threads) {
            thread.join();
        }
    }

    /**
     * Waits for the simulator's port, which a container from a cftlib that ran the simulator in
     * docker still holds. In docker mode compose removes that orphan, but it does so while this
     * is starting, so the bind would otherwise lose a race it can simply wait out.
     */
    private static void awaitAuthServerPort() {
        if (!portInUse()) {
            return;
        }
        var hint = "Another cftlib run holds it, since the simulator now runs in the cftlib JVM "
            + "rather than in a container shared by every run; or a simulator container from an "
            + "older cftlib does, which 'docker rm -f cftlib-rse-idam-simulator-1' removes.";
        // Only docker mode is going to free it, by removing the orphan as it brings compose up.
        // Nothing will in process mode, so say so now rather than after a minute of waiting.
        if (!"docker".equalsIgnoreCase(dependencyMode())) {
            throw new IllegalStateException("Port " + AUTH_SERVER_PORT + " is already in use, and the idam "
                + "simulator runs in this JVM. " + hint);
        }

        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AUTH_SERVER_PORT_WAIT_SECONDS);
        var reported = false;
        while (portInUse() && System.nanoTime() < deadline) {
            if (!reported) {
                System.out.println("**** Cftlib is waiting for port " + AUTH_SERVER_PORT + ", which something "
                    + "else is holding. " + hint);
                reported = true;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (portInUse()) {
            // Reported rather than left to surface as a bind error from deep inside spring.
            throw new IllegalStateException("Port " + AUTH_SERVER_PORT + " was still in use after "
                + AUTH_SERVER_PORT_WAIT_SECONDS + " seconds, so the idam simulator cannot start. " + hint);
        }
    }

    private static boolean portInUse() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", AUTH_SERVER_PORT), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void setConfigProperties() {
        if (!"localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
            System.setProperty("IDAM_API_URL", "https://idam-api.aat.platform.hmcts.net");
            System.setProperty("DM_STORE_BASE_URL", "http://dm-store-aat.service.core-compute-aat.internal");
        }
        if ("localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
            // The runtime creates this user in the IDAM simulator, whose users have this password.
            setUnlessConfigured("IDAM_DATA_STORE_SYSTEM_USER_USERNAME", "data-store-system-user@cftlib.local");
            setUnlessConfigured("IDAM_DATA_STORE_SYSTEM_USER_PASSWORD", "password");
            // The runtime's dm-store stand-in, and the document links it returns, for CDAM and data store.
            setUnlessConfigured("DM_STORE_BASE_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}/dm-store");
            // Data store's own pattern, plus the stand-in's links.
            setUnlessConfigured("CCD_DOCUMENT_URL_PATTERN",
                "https?://((localhost:\\d+/dm-store/documents/[A-Za-z0-9-]+(?:/binary)?)"
                    + "|(dm-store:8080/documents/[A-Za-z0-9-]+(?:/binary)?)"
                    + "|(localhost:8080/hearing-recordings/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                    + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/segments/[0-9]+))");
        }
        var dbHost = "${RSE_LIB_DB_HOST:localhost}";
        var dbPort = "${RSE_LIB_DB_PORT:6432}";

        System.setProperty("USER_PROFILE_DB_HOST", dbHost);
        System.setProperty("USER_PROFILE_DB_PORT", dbPort);
        System.setProperty("USER_PROFILE_DB_USERNAME", "${RSE_LIB_DB_USERNAME:postgres}");
        System.setProperty("USER_PROFILE_DB_PASSWORD", "${RSE_LIB_DB_PASSWORD:postgres}");
        System.setProperty("USER_PROFILE_DB_NAME", "userprofile");
        System.setProperty("USER_PROFILE_DB_OPTIONS", "?stringtype=unspecified");

        System.setProperty("DATA_STORE_DB_HOST", dbHost);
        System.setProperty("DATA_STORE_DB_PORT", dbPort);
        System.setProperty("DATA_STORE_DB_USERNAME", "${RSE_LIB_DB_USERNAME:postgres}");
        System.setProperty("DATA_STORE_DB_PASSWORD", "${RSE_LIB_DB_PASSWORD:postgres}");
        System.setProperty("DATA_STORE_DB_NAME", "datastore");
        System.setProperty("DATA_STORE_DB_OPTIONS", "?stringtype=unspecified");

        System.setProperty("DEFINITION_STORE_DB_HOST", dbHost);
        System.setProperty("DEFINITION_STORE_DB_PORT", dbPort);
        System.setProperty("DEFINITION_STORE_DB_USERNAME", "${RSE_LIB_DB_USERNAME:postgres}");
        System.setProperty("DEFINITION_STORE_DB_PASSWORD", "${RSE_LIB_DB_PASSWORD:postgres}");
        System.setProperty("DEFINITION_STORE_DB_NAME", "definitionstore");

        System.setProperty("ROLE_ASSIGNMENT_DB_HOST", dbHost);
        System.setProperty("ROLE_ASSIGNMENT_DB_PORT", dbPort);
        System.setProperty("ROLE_ASSIGNMENT_DB_NAME", "am");
        System.setProperty("ROLE_ASSIGNMENT_DB_USERNAME", "${RSE_LIB_DB_USERNAME:postgres}");
        System.setProperty("ROLE_ASSIGNMENT_DB_PASSWORD", "${RSE_LIB_DB_PASSWORD:postgres}");

        var esHost = getenv("SEARCH_ELASTIC_HOSTS") != null ? getenv("SEARCH_ELASTIC_HOSTS") : "http://localhost:9200";

        System.setProperty("SEARCH_ELASTIC_HOSTS", esHost);
        System.setProperty("SEARCH_ELASTIC_DATA_HOSTS", esHost);
        // The elasticsearch stub reads the case tables directly, so nothing needs to maintain an
        // index and the definition store has no index to create on import.
        var esStub = esStubMode();
        System.setProperty("elasticsearch.enabled", esStub ? "false" : "true");
        System.setProperty("elasticsearch.failimportiferror", "true");
        if (esStub) {
            System.setProperty("ccd.sdk.decentralised.es-indexer.enabled", "false");
        }

        // Allow more time for definitions to import to reduce test flakeyness
        // These timeouts are large since definition imports can start before elastic search is ready,
        // in which case the import will block waiting for elastic search whilst the transaction is pending.
        System.setProperty("CCD_TX-TIMEOUT_DEFAULT", "240");
        System.setProperty("DEFINITION_STORE_TX_TIMEOUT_DEFAULT", "240");

        System.setProperty("ROLE_ASSIGNMENT_URL", "http://localhost:4096");
        System.setProperty("DEFINITION_STORE_HOST", "http://localhost:4451");
        System.setProperty("CASE_DATA_STORE_BASEURL", "http://localhost:4452");
        System.setProperty("USER_PROFILE_HOST", "http://localhost:4453");

        // Used by AAC manage case assignment
        System.setProperty("CASE_DATA_STORE_BASE_URL", "http://localhost:4452");
        System.setProperty("CCD_DEFINITION_STORE_API_BASE_URL", "http://localhost:4451");

        System.setProperty("CASE_DOCUMENT_AM_URL", "http://localhost:4455");

        if (Arrays.asList(ControlPlane.getEnvVar("RSE_LIB_FEATURES", "").split(",")).contains("servicebus")) {
            // The emulator's development connection string, for services and tests to read as configuration.
            System.setProperty("RSE_LIB_SERVICEBUS_CONNECTION_STRING",
                "Endpoint=sb://localhost;SharedAccessKeyName=RootManageSharedAccessKey;"
                    + "SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;");
        }

        authoriseFeatureServices();

        // Prevent definition store generating errors trying to translate welsh
        System.setProperty("WELSH_TRANSLATION_ENABLED", "false");
    }

    /**
     * Mirrors CftlibConfig's resolution of RSE_LIB_ES. The bootstrapper is deliberately dependency
     * free so that it does not pollute the system classloader, and so cannot read CftlibConfig,
     * which lives in the runtime.
     */
    private static boolean esStubMode() {
        var es = getenv("RSE_LIB_ES");
        if (es != null && !es.isBlank()) {
            return es.trim().equalsIgnoreCase("stub");
        }
        return "process".equalsIgnoreCase(dependencyMode());
    }

    /**
     * The configured dependency mode, as the runtime's own config reads it: unset and blank both
     * mean docker, and surrounding space is not part of the value.
     */
    private static String dependencyMode() {
        var mode = getenv("RSE_LIB_DEPENDENCY_MODE");
        return mode == null || mode.isBlank() ? "docker" : mode.trim();
    }

    // The S2S names of the services each optional feature runs.
    private static final Map<String, List<String>> FEATURE_SERVICES = Map.of(
        "wa", List.of("wa_task_management_api", "wa_case_event_handler", "wa_workflow_api", "wa_task_monitor"),
        "hmc", List.of("hmc_cft_hearing_service")
    );

    /**
     * Data store's and role assignment's default S2S allow lists name the services that call them in an environment.
     * A build that replaces a list would shut out the services its features run, so add them.
     */
    private static void authoriseFeatureServices() {
        var features = Arrays.asList(ControlPlane.getEnvVar("RSE_LIB_FEATURES", "").split(","));
        var services = FEATURE_SERVICES.entrySet().stream()
            .filter(e -> features.contains(e.getKey()))
            .flatMap(e -> e.getValue().stream())
            .toList();
        for (var list : List.of("DATA_STORE_S2S_AUTHORISED_SERVICES", "ROLE_ASSIGNMENT_S2S_AUTHORISED_SERVICES")) {
            var configured = System.getenv(list);
            if (configured != null && !services.isEmpty()) {
                var names = new LinkedHashSet<>(Arrays.asList(configured.split(",")));
                names.addAll(services);
                System.setProperty(list, String.join(",", names));
            }
        }
    }

    // A system property overrides the environment in Spring, so only set one the build has not.
    private static void setUnlessConfigured(String name, String value) {
        if (System.getenv(name) == null) {
            System.setProperty(name, value);
        }
    }

    private static void launchAppOrFailFast(File classpathFile) {
        Thread.currentThread().setUncaughtExceptionHandler(ControlPlane.failFast);
        try {
            launchApp(classpathFile);
        } catch (Throwable e) {
            // Spring boot devtools throws a SilentExitException which we should tolerate
            if (e.getCause() != null && e.getCause().getClass().getSimpleName().equals("SilentExitException")) {
                return;
            }
            throw e;
        }
    }

    @SneakyThrows
    private static void launchApp(File classpathFile) {
        // We must initially use a thread name of 'main' for spring boot devtools to work.
        Thread.currentThread().setName("main");

        var lines = Files.readAllLines(classpathFile.toPath());
        var jars = lines.subList(1, lines.size())
                .stream().map(LibRunner::toURL)
                .collect(Collectors.toList());

        // We inject some custom properties into the classpath to assist logging.
        jars.add(createPropertiesFolder(classpathFile).toURI().toURL());

        var urls = jars.stream().toArray(URL[]::new);
        ClassLoader classLoader = new URLClassLoader(classpathFile.getName(), urls, ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);

        fixTomcat(classLoader);

        var cmd = lines.get(0).split("\\s+");
        var main = cmd[0];
        var args = Arrays.copyOfRange(cmd, 1, cmd.length);
        Class<?> mainClass = classLoader.loadClass(main);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[] {args});

        // Once initialised we can rename the main thread without breaking spring boot devtools.
        Thread.currentThread().setName(classpathFile.getName());
    }

    /**
     * Create a folder containing custom properties for injection to the classpath.
     * These properties are used by our logback configuration to pipe log output
     * into a file per service.
     */
    @SneakyThrows
    private static File createPropertiesFolder(File classpathFile) {
        // Logs go in the cftlib log folder if defined, otherwise the working directory
        var logFolder = System.getenv("RSE_LIB_LOG_FOLDER");
        logFolder = logFolder != null ? logFolder : Paths.get("").toAbsolutePath().normalize().toString();

        var props = Map.of(
                "cftlib_log_file", new File(logFolder, classpathFile.getName() + ".log").getCanonicalPath(),
                "cftlib_console_log_level", classpathFile.getName().contains("application") ? "INFO" : "WARN"
        );

        var dir = Files.createTempDirectory("cftlib");
        var f = new File(dir.toFile(), "cftlib.properties");
        var lines = props.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());
        Files.write(f.toPath(), lines);
        return dir.toFile();
    }

    // TomcatURLStreamHandlerFactory registers a handler by calling
    // java.net.URL setURLStreamHandlerFactory.
    // Since this is on the bootstrap classloader we must disable it.
    @SneakyThrows
    private static void fixTomcat(ClassLoader classLoader) {
        try {
            var c = classLoader.loadClass("org.apache.catalina.webresources.TomcatURLStreamHandlerFactory");
            Method disable = c.getMethod("disable");


            disable.invoke(null);
        } catch (ClassNotFoundException c) {
            // Fine if not using tomcat
        }
    }

    @SneakyThrows
    private static URL toURL(String s) {
        return new File(s).toURI().toURL();
    }
}
