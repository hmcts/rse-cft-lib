package uk.gov.hmcts.rse;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class CftlibTestTaskTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void wiresANativeTestTaskAndPreservesAuthConfiguration() {
        Project project = ProjectBuilder.builder().build();
        project.getExtensions().getExtraProperties().set("cftlib.nativeTests", true);
        project.getPlugins().apply(CftLibPlugin.class);
        var task = (CftlibTestTask) project.getTasks().getByName("cftlibTest");
        var source = project.getExtensions().getByType(SourceSetContainer.class).getByName("cftlibTest");
        assertThat(task.getTestClassesDirs()).isEqualTo(source.getOutput().getClassesDirs());
        assertThat(task.getJavaLauncher().get().getMetadata().getLanguageVersion().asInt()).isEqualTo(21);
        assertThat(task.getMaxParallelForks()).isEqualTo(1);
        assertThat(task.getForkEvery()).isZero();
        var legacyManifest = project.getTasks().named("manifestTest").get();
        assertThat(legacyManifest.getEnabled()).isFalse();
        assertThat(task.getSystemProperties().get("rse.lib.manifests").toString()).contains("runtime");
        CftlibForkOptions.configure(project, task, AuthMode.Local);
        assertThat(task.getEnvironment()).containsEntry("RSE_LIB_AUTH-MODE", "localAuth");
    }

    @Test
    public void supportsConsumerConfigurationAndRunsSelectedMethods() throws Exception {
        var directory = temporaryFolder.getRoot().toPath();
        Files.writeString(directory.resolve("gradle.properties"), "cftlib.nativeTests=true\n");
        Files.writeString(directory.resolve("settings.gradle"), """
            rootProject.name = 'native-runner-test'
            includeBuild 'sdk-fixture'
            """);
        var sdk = directory.resolve("sdk-fixture");
        Files.createDirectories(sdk.resolve("src/main/resources"));
        Files.writeString(sdk.resolve("settings.gradle"), "rootProject.name = 'sdk-fixture'");
        Files.writeString(sdk.resolve("build.gradle"),
            "plugins { id 'java-library' }; group = 'fixture'; version = '1'");
        Files.writeString(sdk.resolve("src/main/resources/sdk.properties"), "sdk=true");
        Files.writeString(directory.resolve("build.gradle"), """
            plugins {
                id 'com.github.hmcts.rse-cft-lib'
            }
            configurations.configureEach { dependencies.clear() }
            repositories { mavenCentral() }
            dependencies {
                cftlibTestImplementation 'org.junit.jupiter:junit-jupiter:5.12.2'
                cftlibTestRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.12.2'
            }
            // Reproduce Docker Compose's configuration-time dependency walk before a late SDK dependency.
            tasks.cftlibTest.taskDependencies.getDependencies(tasks.cftlibTest)
            afterEvaluate { dependencies.add('implementation', 'fixture:sdk-fixture:1') }
            // Reproduce the broad Test configuration used by e2e, nfdiv and sptribs.
            tasks.withType(Test).configureEach {
                maxParallelForks = 4
                forkEvery = 1
            }
            // Reproduce e2e's legacy hook: it must configure, but must never execute.
            tasks.named('manifestTest') {
                doLast {
                    def manifest = layout.buildDirectory.file('cftlib/libTest').get().asFile
                    manifest.text = manifest.text.replace('ConsoleLauncher --select-package=',
                        'ConsoleLauncher execute --select-package=')
                }
            }
            tasks.withType(uk.gov.hmcts.rse.CftlibTask).configureEach {
                authMode = uk.gov.hmcts.rse.AuthMode.Local
                environment 'SHARED_SETTING', 'shared'
                systemProperty 'shared.setting', 'shared'
                classpath += files('extra-resources')
                doFirst { environment 'SHARED_DO_FIRST', 'shared' }
            }
            tasks.named('cftlibTest') {
                // Run fixtures without starting the CCD stack.
                setDependsOn([tasks.named('cftlibTestClasses'), tasks.named('manifestTest')])
                systemProperty 'junit.jupiter.execution.parallel.enabled', 'true'
            }
            tasks.register('verifyForkConfiguration') {
                dependsOn tasks.named('cftlibTest')
                doLast {
                    assert tasks.cftlibTest.maxParallelForks == 1
                    assert tasks.cftlibTest.forkEvery == 0
                    assert tasks.test.maxParallelForks == 4
                    assert tasks.test.forkEvery == 1
                }
            }
            """);
        Files.createDirectories(directory.resolve("extra-resources"));
        Files.writeString(directory.resolve("extra-resources/shared.properties"), "shared=true");
        var source = directory.resolve("src/cftlibTest/java/example/SampleTest.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package example;
            import org.junit.jupiter.api.Test;
            public class SampleTest {
                @Test void passes() {
                    if (!"shared".equals(System.getenv("SHARED_SETTING"))
                        || !"shared".equals(System.getProperty("shared.setting"))) {
                        throw new AssertionError("Shared CFTLib configuration was not applied");
                    }
                    if (!"shared".equals(System.getenv("SHARED_DO_FIRST"))
                        || !"true".equals(System.getProperty("junit.jupiter.execution.parallel.enabled"))
                        || SampleTest.class.getResource("/shared.properties") == null
                        || SampleTest.class.getResource("/sdk.properties") == null) {
                        throw new AssertionError("Consumer task settings were not preserved");
                    }
                    if (!"localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
                        throw new AssertionError("Local authentication was not configured");
                    }
                }
                @Test void fails() { throw new AssertionError("expected failure"); }
            }
            """);
        var result = GradleRunner.create().withProjectDir(temporaryFolder.getRoot()).withPluginClasspath()
            .withArguments("cftlibTest", "--tests", "SampleTest.passes", "verifyForkConfiguration").build();
        assertThat(result.task(":manifestTest").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(result.getOutput()).contains("manifestTest is deprecated and disabled",
            "overriding maxParallelForks=4 and forkEvery=1 with maxParallelForks=1 and forkEvery=0");
        var report = directory.resolve("build/test-results/cftlibTest/TEST-example.SampleTest.xml");
        assertThat(Files.readString(report)).contains("name=\"passes()\"").doesNotContain("name=\"fails()\"");
    }
}
