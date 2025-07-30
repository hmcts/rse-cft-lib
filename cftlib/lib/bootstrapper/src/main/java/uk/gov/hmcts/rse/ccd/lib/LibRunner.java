package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.lang.System.getenv;

import lombok.SneakyThrows;

public class LibRunner {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("**** cftlib bootstrap");
        Thread.currentThread().setUncaughtExceptionHandler(ControlPlane.failFast);
        doRun(args);
    }

    @SneakyThrows
    private static void doRun(String[] args) {
        setConfigProperties();
        var threads = new ArrayList<Thread>();
        {
            var runtime = args[0];
            var t = new Thread(() -> launchAppOrFailFast(new File(runtime)));
            t.setName("runtime");
            t.start();
            threads.add(t);
        }

        // Cannot start spring boot apps until Oauth server ready.
        ControlPlane.waitForAuthServer();

        var rest = Arrays.copyOfRange(args, 1, args.length);
        Arrays.stream(rest).forEach(f -> {
            var t = new Thread(() -> launchAppOrFailFast(new File(f)));
            t.start();
            threads.add(t);
        });
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private static void setConfigProperties() {
        if (!"localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
            System.setProperty("IDAM_API_URL", "https://idam-api.aat.platform.hmcts.net");
            System.setProperty("DM_STORE_BASE_URL", "http://dm-store-aat.service.core-compute-aat.internal");
        }
        var dbHost = "${RSE_LIB_DB_HOST:localhost}";
        var dbPort = "${RSE_LIB_DB_PORT:6432}";

        System.setProperty("USER_PROFILE_DB_HOST", dbHost);
        System.setProperty("USER_PROFILE_DB_PORT", dbPort);
        System.setProperty("USER_PROFILE_DB_USERNAME", "postgres");
        System.setProperty("USER_PROFILE_DB_PASSWORD", "postgres");
        System.setProperty("USER_PROFILE_DB_NAME", "userprofile");
        System.setProperty("USER_PROFILE_DB_OPTIONS", "?stringtype=unspecified");

        System.setProperty("DATA_STORE_DB_HOST", dbHost);
        System.setProperty("DATA_STORE_DB_PORT", dbPort);
        System.setProperty("DATA_STORE_DB_USERNAME", "postgres");
        System.setProperty("DATA_STORE_DB_PASSWORD", "postgres");
        System.setProperty("DATA_STORE_DB_NAME", "datastore");
        System.setProperty("DATA_STORE_DB_OPTIONS", "?stringtype=unspecified");

        System.setProperty("DEFINITION_STORE_DB_HOST", dbHost);
        System.setProperty("DEFINITION_STORE_DB_PORT", dbPort);
        System.setProperty("DEFINITION_STORE_DB_USERNAME", "postgres");
        System.setProperty("DEFINITION_STORE_DB_PASSWORD", "postgres");
        System.setProperty("DEFINITION_STORE_DB_NAME", "definitionstore");

        System.setProperty("ROLE_ASSIGNMENT_DB_HOST", dbHost);
        System.setProperty("ROLE_ASSIGNMENT_DB_PORT", dbPort);
        System.setProperty("ROLE_ASSIGNMENT_DB_NAME", "am");
        System.setProperty("ROLE_ASSIGNMENT_DB_USERNAME", "postgres");
        System.setProperty("ROLE_ASSIGNMENT_DB_PASSWORD", "postgres");

        var esHost = getenv("SEARCH_ELASTIC_HOSTS") != null ? getenv("SEARCH_ELASTIC_HOSTS") : "http://localhost:9200";

        System.setProperty("SEARCH_ELASTIC_HOSTS", esHost);
        System.setProperty("SEARCH_ELASTIC_DATA_HOSTS", esHost);
        System.setProperty("elasticsearch.enabled", "true");
        System.setProperty("elasticsearch.failimportiferror", "true");

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

        // Prevent definition store generating errors trying to translate welsh
        System.setProperty("WELSH_TRANSLATION_ENABLED", "false");
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
