package uk.gov.hmcts.rse;

import org.gradle.api.InvalidUserDataException;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.platform.console.ConsoleLauncher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CftlibTestFilteringTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void delegatesClassFilteringToJUnit() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        var task = project.getTasks().create("cftlibTest", CftlibExec.class);
        assertThat(task.getTestArguments()).containsExactly("execute", "--select-package=uk.gov.hmcts");
        var cases = Map.of(
            List.of("uk.gov.hmcts.filterfixtures.FilterAlphaTest"), 1,
            List.of("FilterBetaTest"), 1,
            List.of("Filter*Test"), 2,
            List.of("uk.gov.hmcts.filterfixtures.*"), 2,
            List.of("FilterAlpha*", "*BetaTest"), 2,
            List.of("FilterMissing"), 0,
            List.of("FilterAlphaTest|FilterBetaTest"), 0
        );
        cases.forEach((patterns, expected) -> {
            task.setTests(patterns);
            var arguments = new ArrayList<>(task.getTestArguments());
            arguments.add("--disable-ansi-colors");
            arguments.add("--disable-banner");
            var output = new StringWriter();
            var result = ConsoleLauncher.run(new PrintWriter(output), new PrintWriter(output),
                arguments.toArray(String[]::new));
            assertThat(result.getExitCode()).as("patterns %s: %s", patterns, output).isEqualTo(expected == 0 ? 2 : 0);
            assertThat(output.toString()).contains(expected + " tests successful");
        });
        var boot = project.getTasks().create("bootWithCCD", CftlibExec.class);
        assertThatThrownBy(() -> boot.setTests(List.of("FilterAlphaTest")))
            .isInstanceOf(InvalidUserDataException.class).hasMessageContaining("cftlibTest only");
    }

    @Test
    public void acceptsCliFiltersAndRegeneratesTheExistingManifest() throws Exception {
        var directory = temporaryFolder.getRoot().toPath();
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'filter-test'");
        Files.writeString(directory.resolve("build.gradle"), """
            plugins { id 'com.github.hmcts.rse-cft-lib' }
            configurations.configureEach { dependencies.clear() }
            tasks.withType(uk.gov.hmcts.rse.CftlibExec).configureEach {
                authMode = uk.gov.hmcts.rse.AuthMode.Local
                environment 'LEGACY_SETTING', 'preserved'
            }
            tasks.named('manifestTest') {
                doLast {
                    def manifest = layout.buildDirectory.file('cftlib/libTest').get().asFile
                    manifest.text = manifest.text.replace('ConsoleLauncher --select-package=',
                        'ConsoleLauncher execute --select-package=')
                    println 'LEGACY_MANIFEST_HOOK'
                }
            }
            tasks.named('cftlibTest') {
                setDependsOn([tasks.named('manifestTest')])
                actions.clear()
                doLast {
                    assert getTaskIdentity().type.name == 'uk.gov.hmcts.rse.CftlibExec'
                    assert authMode == uk.gov.hmcts.rse.AuthMode.Local
                    def legacySetting = environment.LEGACY_SETTING
                    assert legacySetting == 'preserved'
                    def manifest = layout.buildDirectory.file('cftlib/libTest').get().asFile
                    println('JUNIT_ARGS=' + manifest.readLines().first())
                }
            }
            """);
        var runner = GradleRunner.create().withProjectDir(temporaryFolder.getRoot()).withPluginClasspath();
        var filtered = runner.withArguments("cftlibTest", "--tests", "FilterAlphaTest", "--tests", "*BetaTest").build();
        assertThat(filtered.getOutput()).contains("LEGACY_MANIFEST_HOOK", "FilterAlphaTest", "BetaTest",
            "--include-classname=", "--fail-if-no-tests");
        var unfiltered = runner.withArguments("cftlibTest").build();
        assertThat(unfiltered.task(":manifestTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(unfiltered.getOutput()).contains("LEGACY_MANIFEST_HOOK",
            "JUNIT_ARGS=org.junit.platform.console.ConsoleLauncher execute --select-package=uk.gov.hmcts")
            .doesNotContain("--include-classname", "--fail-if-no-tests");
    }
}
