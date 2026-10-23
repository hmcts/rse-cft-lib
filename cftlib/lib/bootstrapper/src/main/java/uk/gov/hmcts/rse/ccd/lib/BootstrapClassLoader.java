package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Share bootstrapper types without exposing the consumer's Spring, logging or other dependencies to services.
 */
final class BootstrapClassLoader extends ClassLoader {
    private final ClassLoader shared = ControlPlane.class.getClassLoader();
    private final String location = ControlPlane.class.getProtectionDomain().getCodeSource()
        .getLocation().toExternalForm();

    BootstrapClassLoader() {
        super(ClassLoader.getPlatformClassLoader());
    }

    @Override
    public URL getResource(String name) {
        if ("logback.xml".equals(name)) {
            return BootstrapClassLoader.class.getResource("service-logback.xml");
        }
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if ("logback.xml".equals(name)) {
            URL logging = getResource(name);
            return logging == null ? Collections.emptyEnumeration()
                : Collections.enumeration(java.util.List.of(logging));
        }
        return super.getResources(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL resource = shared.getResource(path);
        String expected = location.endsWith("/") ? location + path : "jar:" + location + "!/" + path;
        if (resource != null && expected.equals(resource.toExternalForm())) {
            return shared.loadClass(name);
        }
        return super.loadClass(name, resolve);
    }
}
