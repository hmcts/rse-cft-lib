package uk.gov.hmcts.rse;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CftlibTestTaskTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void wiresANativeTestTaskAndPreservesAuthConfiguration() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(CftLibPlugin.class);
        var task = (CftlibTestTask) project.getTasks().getByName("cftlibTest");
        var source = project.getExtensions().getByType(SourceSetContainer.class).getByName("cftlibTest");
        assertThat(task.getTestClassesDirs()).isEqualTo(source.getOutput().getClassesDirs());
        assertThat(task.getJavaLauncher().get().getMetadata().getLanguageVersion().asInt()).isEqualTo(21);
        assertThat(task.getMaxParallelForks()).isEqualTo(1);
        assertThat(task.getForkEvery()).isZero();
        assertThat(project.getTasks().findByName("manifestTest")).isNull();
        assertThat(task.getSystemProperties().get("rse.lib.manifests").toString()).contains("runtime");
        CftlibForkOptions.configure(project, task, AuthMode.Local);
        assertThat(task.getEnvironment()).containsEntry("RSE_LIB_AUTH-MODE", "localAuth");
    }

    @Test
    public void runsSelectedMethodsWithConfiguredAuthAndNativeReports() throws Exception {
        var directory = temporaryFolder.getRoot().toPath();
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'native-runner-test'");
        Files.writeString(directory.resolve("build.gradle"), """
            plugins { id 'com.github.hmcts.rse-cft-lib' }
            configurations.configureEach { dependencies.clear() }
            repositories { mavenCentral() }
            dependencies {
                cftlibTestImplementation 'org.junit.jupiter:junit-jupiter:5.12.2'
                cftlibTestRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.12.2'
            }
            tasks.named('cftlibTest') {
                authMode = uk.gov.hmcts.rse.AuthMode.Local
                setDependsOn([tasks.named('cftlibTestClasses')])
            }
            """);
        var source = directory.resolve("src/cftlibTest/java/example/SampleTest.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package example;
            import org.junit.jupiter.api.Test;
            public class SampleTest {
                @Test void passes() {
                    if (!"localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
                        throw new AssertionError("Local authentication was not configured");
                    }
                }
                @Test void fails() { throw new AssertionError("expected failure"); }
            }
            """);
        runner("cftlibTest", "--tests", "SampleTest.passes").build();
        var report = directory.resolve("build/test-results/cftlibTest/TEST-example.SampleTest.xml");
        assertThat(Files.readString(report)).contains("name=\"passes()\"").doesNotContain("name=\"fails()\"");

    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create().withProjectDir(temporaryFolder.getRoot()).withPluginClasspath()
            .withArguments(List.of(arguments));
    }
}
