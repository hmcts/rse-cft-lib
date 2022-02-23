package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import lombok.SneakyThrows;

public class LibRunner {
    public static void main(String[] args) throws Exception {
        var threads = new ArrayList<Thread>();
        Arrays.stream(args).forEach(f -> {
            var t = new Thread(() -> launchApp(f));
            t.setName(f);
            threads.add(t);
            t.start();
        });
        for (Thread thread : threads) {
            thread.join();
        }

        System.exit(0);
    }

    @SneakyThrows
    private static void launchApp(String classpathFile) {
        var lines = Files.readAllLines(new File(classpathFile).toPath());
        var main = lines.get(0);
        var jars = lines.subList(1, lines.size());
        var urls = jars.stream().map(LibRunner::toURL).toArray(URL[]::new);
        ClassLoader classLoader = new URLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(classLoader);

        fixTomcat(classLoader);

        Class<?> mainClass = classLoader.loadClass(main);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[] {new String[0]});
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
        }
    }

    private static URL toURL(String s) {
        try {
            return new File(s).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
