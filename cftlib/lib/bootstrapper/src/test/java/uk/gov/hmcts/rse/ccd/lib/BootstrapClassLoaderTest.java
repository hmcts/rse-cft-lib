package uk.gov.hmcts.rse.ccd.lib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.SpringVersion;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BootstrapClassLoaderTest {
    @TempDir
    Path directory;

    @Test
    void sharesBootstrapStateWhileKeepingServiceSpringIsolated() throws Exception {
        ClassLoader parent = new BootstrapClassLoader();
        assertSame(ControlPlane.class, parent.loadClass(ControlPlane.class.getName()));
        assertSame(CFTLib.class, parent.loadClass(CFTLib.class.getName()));
        assertSame(java.sql.Connection.class, parent.loadClass("java.sql.Connection"));
        assertThrows(ClassNotFoundException.class, () -> parent.loadClass(SpringVersion.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> parent.loadClass(getClass().getName()));
        assertNull(parent.getResource("org/springframework/core/SpringVersion.class"));
        assertNull(ControlPlane.class.getClassLoader().getResource("logback.xml"));
        assertNotNull(parent.getResource("logback.xml"));
        assertEquals(parent.getResource("logback.xml"), parent.getResources("logback.xml").nextElement());
        URL spring = SpringVersion.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader service = new URLClassLoader(new URL[] {spring}, parent)) {
            assertSame(ControlPlane.class, service.loadClass(ControlPlane.class.getName()));
            assertNotSame(SpringVersion.class, service.loadClass(SpringVersion.class.getName()));
        }
    }

    @Test
    void alsoSharesBootstrapTypesWhenPackagedAsAJar() throws Exception {
        Path classes = Path.of(ControlPlane.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path jar = directory.resolve("bootstrapper with spaces.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(classes)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("uk/gov/hmcts/rse/ccd/lib/service-logback.xml"));
            try (var logging = BootstrapClassLoader.class.getResourceAsStream("service-logback.xml")) {
                logging.transferTo(output);
            }
            output.closeEntry();
        }
        try (URLClassLoader owner = new URLClassLoader(new URL[] {jar.toUri().toURL()},
            ClassLoader.getPlatformClassLoader())) {
            var constructor = owner.loadClass(BootstrapClassLoader.class.getName()).getDeclaredConstructor();
            constructor.setAccessible(true);
            ClassLoader parent = (ClassLoader) constructor.newInstance();
            assertSame(owner.loadClass(ControlPlane.class.getName()), parent.loadClass(ControlPlane.class.getName()));
            assertSame(owner.loadClass(CFTLib.class.getName()), parent.loadClass(CFTLib.class.getName()));
            assertNotNull(parent.getResource("logback.xml"));
            assertThrows(ClassNotFoundException.class, () -> parent.loadClass(getClass().getName()));
        }
    }
}
