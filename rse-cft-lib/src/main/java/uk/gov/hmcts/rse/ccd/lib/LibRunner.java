package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

public class LibRunner {
    public static void main(String[] args) throws Exception {
        launchApp(args[0], args);
    }

    private static void launchApp(String classpathFile, String[] args) throws Exception {
        var jars = Files.readAllLines(new File(classpathFile).toPath());
        var urls = jars.stream().map(LibRunner::toURL).toArray(URL[]::new);
        ClassLoader classLoader = new URLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(classLoader);
        Class<?> mainClass = classLoader.loadClass("uk.gov.hmcts.ccd.UserProfileApplication");
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
