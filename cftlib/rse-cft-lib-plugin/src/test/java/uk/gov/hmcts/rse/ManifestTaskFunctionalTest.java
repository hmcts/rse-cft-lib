package uk.gov.hmcts.rse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ManifestTaskFunctionalTest {

    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder();

    @Test
    public void rerunsWhenClasspathChanges() throws Exception {
        writeBuildFile("3.12.0");

        BuildResult firstRun = runner().build();
        assertThat(firstRun.task(":writeManifest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        BuildResult secondRun = runner().build();
        assertThat(secondRun.task(":writeManifest").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        writeBuildFile("3.13.0");

        BuildResult thirdRun = runner().build();
        assertThat(thirdRun.task(":writeManifest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(manifestFile().toPath())).contains("commons-lang3-3.13.0.jar");
    }

    private GradleRunner runner() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("writeManifest");
    }

    private File manifestFile() {
        return new File(testProjectDir.getRoot(), "build/manifest.txt");
    }

    private void writeBuildFile(String version) throws Exception {
        Path settingsFile = testProjectDir.getRoot().toPath().resolve("settings.gradle");
        Files.writeString(settingsFile, "rootProject.name = 'manifest-task-test'\n");

        String buildFile = """
            buildscript {
                dependencies {
                    classpath files(%s)
                }
            }

            import uk.gov.hmcts.rse.ManifestTask

            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            configurations {
                manifestClasspath
            }

            dependencies {
                manifestClasspath 'org.apache.commons:commons-lang3:%s'
            }

            tasks.register('writeManifest', ManifestTask) {
                classpath.from(configurations.manifestClasspath)
                mainClassName.set('example.Main')
                manifestArgs.set(['--rse.lib.service_name=test'])
                manifestFile.set(layout.buildDirectory.file('manifest.txt'))
            }
            """.formatted(pluginClasspathDeclaration(), version);

        Files.writeString(testProjectDir.getRoot().toPath().resolve("build.gradle"), buildFile);
    }

    private String pluginClasspathDeclaration() throws Exception {
        Path metadataPath = Path.of("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties");
        Properties properties = new Properties();
        try (var input = new FileInputStream(metadataPath.toFile())) {
            properties.load(input);
        }

        String implementationClasspath = properties.getProperty("implementation-classpath");
        assertThat(implementationClasspath).isNotBlank();

        return java.util.Arrays.stream(implementationClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator)))
            .map(path -> "'" + path.replace("\\", "\\\\") + "'")
            .collect(Collectors.joining(", "));
    }
}
