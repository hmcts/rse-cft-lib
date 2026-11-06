package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibRunnerReadinessTest {
    @Test
    void waitsForSupervisedAppsButNotPlainSupportProcesses(@TempDir Path directory) throws Exception {
        var agent = directory.resolve("cftlib-agent.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(agent))) {
            zip.putNextEntry(new ZipEntry("uk/gov/hmcts/rse/ccd/lib/LibAgent.class"));
            zip.closeEntry();
        }
        var localClasses = Files.createDirectories(directory.resolve("local/build/classes/java/main"));
        var agentClass = localClasses.resolve("uk/gov/hmcts/rse/ccd/lib/LibAgent.class");
        Files.createDirectories(agentClass.getParent());
        Files.createFile(agentClass);
        // libTest runs JUnit, which starts the consumer's supervised Spring application.
        var manifests = List.of(
            manifest(directory, "runtime", "Runtime", ""),
            manifest(directory, "ccd-data-store-api", "Application", agent.toString()),
            manifest(directory, "libTest", "org.junit.platform.console.ConsoleLauncher", agent.toString()),
            manifest(directory, "local-service", "Application", localClasses.toString()),
            manifest(directory, "rse-idam-simulator", "Application", ""),
            manifest(directory, "wiremock", "wiremock.Run", ""),
            manifest(directory, "support", "uk.gov.hmcts.reform.pcsdemo.support.DemoSupport", "")
        );

        assertThat(LibRunner.supervisedApps(manifests)).isEqualTo(3);
    }

    private String manifest(Path directory, String name, String main, String classpath) throws Exception {
        return Files.writeString(directory.resolve(name), main + "\n" + classpath + "\n").toString();
    }
}
