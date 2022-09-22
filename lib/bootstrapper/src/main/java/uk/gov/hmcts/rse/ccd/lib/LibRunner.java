package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.System.getenv;

import lombok.SneakyThrows;

public class LibRunner {
    public static void main(String[] args) throws Exception {
        try {
            doRun(args);
        } catch (Exception e) {
            System.out.println("*** cftlib failed to start ***");
            System.out.println("This is a cftlib bug, please report it in the rse-devtools slack channel");
            System.out.println("https://hmcts-reform.slack.com/archives/C033F1GDD6Z");
            e.printStackTrace();

            // Immediately terminate upon an unhandled error in the runner.
            // This will ensure the JVM terminates even if we've started other threads.
            Runtime.getRuntime().halt(-1);
            // Unreachable
            throw e;
        }
    }

    @SneakyThrows
    private static void doRun(String[] args) {
        Thread.currentThread().setName("**** cftlib bootstrap");
        setConfigProperties();
        if (args.length == 0) {
            args = extractRuntime();
        }
        var threads = new ArrayList<Thread>();
        {
            var runtime = args[0];
            var t = new Thread(() -> launchApp(new File(runtime)));
            t.setName("runtime");
            t.start();
            threads.add(t);
        }

        // Cannot start spring boot apps until Oauth server ready.
        ControlPlane.waitForAuthServer();

        var rest = Arrays.copyOfRange(args, 1, args.length);
        Arrays.stream(rest).forEach(f -> {
            var t = new Thread(() -> launchApp(new File(f)));
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
        System.setProperty("CCD_TX-TIMEOUT_DEFAULT", "120");
        System.setProperty("DEFINITION_STORE_TX_TIMEOUT_DEFAULT", "120");

        System.setProperty("ROLE_ASSIGNMENT_URL", "http://localhost:4096");
        System.setProperty("DEFINITION_STORE_HOST", "http://localhost:4451");
        System.setProperty("CASE_DATA_STORE_BASEURL", "http://localhost:4452");
        System.setProperty("USER_PROFILE_HOST", "http://localhost:4453");

        // Used by AAC manage case assignment
        System.setProperty("CASE_DATA_STORE_BASE_URL", "http://localhost:4452");
        System.setProperty("CCD_DEFINITION_STORE_API_BASE_URL", "http://localhost:4451");
    }


    @SneakyThrows
    public static String[] extractRuntime() throws IOException {
        // Turn off devtools when packaged as a jar.
        System.setProperty("spring.devtools.restart.enabled", "false");
        var is = LibRunner.class.getResourceAsStream("/cftlib-runtime.zip");

        if (null != is) {
            try (ZipInputStream zipIn = new ZipInputStream(is)) {
                for (ZipEntry ze; (ze = zipIn.getNextEntry()) != null; ) {
                    var targetDir = Paths.get("");
                    Path resolvedPath = targetDir.resolve(ze.getName()).normalize();
                    if (ze.isDirectory()) {
                        Files.createDirectories(resolvedPath);
                    } else {
                        Files.createDirectories(resolvedPath.getParent());
                        Files.copy(zipIn, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        var result =
            Stream.concat(Stream.of("build/cftlib/runtime_packed"),
                    Files.list(Paths.get("build/cftlib"))
                        .filter(x -> !x.getFileName().endsWith("runtime_packed"))
                        .map(x -> x.toAbsolutePath().toString()))
                .toArray(String[]::new);
        return result;
    }

    @SneakyThrows
    private static void launchApp(File classpathFile) {
        // We must initially use a thread name of 'main' for spring boot devtools to work.
        Thread.currentThread().setName("main");

        var lines = Files.readAllLines(classpathFile.toPath());
        var jars = lines.subList(1, lines.size())
                .stream().map(LibRunner::toURL)
                .collect(Collectors.toList());

        var props = Map.of(
                "cftlib_name", classpathFile.getName()
        );
        jars.add(createPropertiesFolder(props).toURI().toURL());

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

    @SneakyThrows
    private static File createPropertiesFolder(Map<String, String> properties) {
        var dir = Files.createTempDirectory("cftlib");
        var f = new File(dir.toFile(), "cftlib.properties");
        var lines = properties.entrySet().stream()
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
