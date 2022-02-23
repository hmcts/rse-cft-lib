package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

import uk.gov.hmcts.rse.ccd.lib.impl.ComposeRunner;

public class LibRunner {
    public static void main(String[] args) throws Exception {
        launchCompose();
        launchApp(args[0], args);
        System.exit(0);
    }

    private static void launchCompose() {
        new ComposeRunner.RunListener();
    }

    private static void launchApp(String classpathFile, String[] args) throws Exception {
        var lines = Files.readAllLines(new File(classpathFile).toPath());
        var main = lines.get(0);
        var jars = lines.subList(1, lines.size());
        var urls = jars.stream().map(LibRunner::toURL).toArray(URL[]::new);
        ClassLoader classLoader = new URLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(classLoader);
        Class<?> mainClass = classLoader.loadClass(main);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[] {new String[0]});
    }

    private static URL toURL(String s) {
        try {
            return new File(s).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
