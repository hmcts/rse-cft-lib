package uk.gov.hmcts.rse.ccd.lib.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class CftlibExtensionTest {
    @TempDir
    Path directory;

    @Test
    void startsOnceBeforeSpringPreparesAnyTestInstance() throws Exception {
        Path manifest = directory.resolve("runtime manifest");
        Files.writeString(manifest, FixtureRuntime.class.getName() + "\n"
            + Path.of(FixtureRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + "\n");
        System.setProperty("rse.lib.manifests", manifest.toString());
        System.setProperty("logging.file.name", directory.resolve("consumer.log").toString());
        try {
            var summary = new SummaryGeneratingListener();
            var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(FirstFixture.class), selectClass(SecondFixture.class)).build();
            LauncherFactory.create().execute(request, summary);
            assertEquals(2, summary.getSummary().getTestsSucceededCount(),
                () -> summary.getSummary().getFailures().toString());
            assertEquals(0, summary.getSummary().getTotalFailureCount());
            // Legacy ConsoleLauncher executes tests in an already-started JVM without the native property.
            System.clearProperty("rse.lib.manifests");
            var legacySummary = new SummaryGeneratingListener();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(FirstFixture.class)).build(), legacySummary);
            assertEquals(1, legacySummary.getSummary().getTestsSucceededCount());
            assertEquals("1", System.getProperty("fixture.starts"));
            assertTrue(Files.readString(directory.resolve("consumer.log")).contains("consumer logging is configured"));
        } finally {
            System.clearProperty("rse.lib.manifests");
            System.clearProperty("logging.file.name");
            System.clearProperty("fixture.started");
            System.clearProperty("fixture.starts");
        }
    }

    @Configuration
    static class Consumer {
        @Bean
        String ready() {
            assertTrue(Boolean.getBoolean("fixture.started"), "Services must start before consumer beans");
            return "ready";
        }
    }

    @SpringBootTest(classes = Consumer.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class FirstFixture extends CftlibTest {
        @Autowired
        String ready;

        @Test
        void usesTheStartedServices() {
            assertEquals("ready", ready);
            LoggerFactory.getLogger("native.consumer").info("consumer logging is configured");
        }
    }

    @SpringBootTest(classes = Consumer.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class SecondFixture extends CftlibTest {
        @Test
        void reusesTheStartedServices() {
            assertTrue(Boolean.getBoolean("fixture.started"));
        }
    }
}
