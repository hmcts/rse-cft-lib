package uk.gov.hmcts.rse;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.platform.console.ConsoleLauncher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CftlibTestExecTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void leavesDefaultPackageSelectionUnchanged() {
        assertThat(task().getTestArguments()).containsExactly("execute", "--select-package=uk.gov.hmcts");
    }

    @Test
    public void selectsAFullyQualifiedClass() {
        assertSelection(List.of("uk.gov.hmcts.filterfixtures.FilterAlphaTest"), "FilterAlphaTest", "FilterBetaTest");
    }

    @Test
    public void selectsASimpleClassName() {
        assertSelection(List.of("FilterBetaTest"), "FilterBetaTest", "FilterAlphaTest");
    }

    @Test
    public void selectsWildcardMatchesAndMultiplePatterns() {
        var task = task();
        task.setTests(List.of("uk.gov.hmcts.*.FilterAlpha*", "FilterBeta*"));
        StringWriter output = new StringWriter();
        assertThat(run(task, output)).isZero();
        assertThat(output.toString()).contains("FilterAlphaTest", "FilterBetaTest", "2 tests successful");
    }

    @Test
    public void failsWhenNoTestsMatch() {
        var task = task();
        task.setTests(List.of("MissingClass"));
        assertThat(run(task, new StringWriter())).isEqualTo(2);
    }

    @Test
    public void writesCurrentFiltersIntoTheManifest() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(temporaryFolder.getRoot()).build();
        project.getPlugins().apply(CftLibPlugin.class);
        project.getConfigurations().forEach(configuration -> configuration.getDependencies().clear());
        var task = (CftlibTestExec) project.getTasks().getByName("cftlibTest");
        var manifest = project.getTasks().getByName("manifestTest");
        var file = project.getLayout().getBuildDirectory().file("cftlib/libTest").get().getAsFile().toPath();

        task.setTests(List.of("FilterAlphaTest"));
        manifest.getActions().forEach(action -> action.execute(manifest));
        assertThat(Files.readString(file)).startsWith("org.junit.platform.console.ConsoleLauncher execute ")
            .contains("FilterAlphaTest", "--fail-if-no-tests");

        task.setTests(List.of());
        manifest.getActions().forEach(action -> action.execute(manifest));
        assertThat(Files.readAllLines(file).getFirst()).isEqualTo(
            "org.junit.platform.console.ConsoleLauncher execute --select-package=uk.gov.hmcts");
    }

    @Test
    public void acceptsRepeatedTestsOptionsFromTheCommandLine() throws Exception {
        Files.writeString(temporaryFolder.getRoot().toPath().resolve("settings.gradle"),
            "rootProject.name = 'filter-test'");
        Files.writeString(temporaryFolder.getRoot().toPath().resolve("build.gradle"), """
            plugins { id 'com.github.hmcts.rse-cft-lib' }
            configurations.configureEach { dependencies.clear() }
            tasks.named('cftlibTest') {
                setDependsOn([tasks.named('manifestTest')])
                actions.clear()
                doLast {
                    def manifest = layout.buildDirectory.file('cftlib/libTest').get().asFile
                    println('JUNIT_ARGS=' + manifest.readLines().first())
                }
            }
            """);
        var result = GradleRunner.create().withProjectDir(temporaryFolder.getRoot()).withPluginClasspath()
            .withArguments("cftlibTest", "--tests", "FilterAlphaTest", "--tests", "*BetaTest", "--offline")
            .build();
        assertThat(result.getOutput()).contains(
            "JUNIT_ARGS=org.junit.platform.console.ConsoleLauncher execute --select-package=uk.gov.hmcts",
            "FilterAlphaTest", "BetaTest", "--fail-if-no-tests");
        var unfiltered = GradleRunner.create().withProjectDir(temporaryFolder.getRoot()).withPluginClasspath()
            .withArguments("cftlibTest", "--offline").build();
        assertThat(unfiltered.getOutput()).contains("execute --select-package=uk.gov.hmcts")
            .doesNotContain("--include-classname", "--fail-if-no-tests");
    }

    private CftlibTestExec task() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        return project.getTasks().create("cftlibTest", CftlibTestExec.class);
    }

    private void assertSelection(List<String> patterns, String included, String excluded) {
        var task = task();
        task.setTests(patterns);
        StringWriter output = new StringWriter();
        assertThat(run(task, output)).isZero();
        assertThat(output.toString()).contains(included, "1 tests successful").doesNotContain(excluded);
    }

    private int run(CftlibTestExec task, StringWriter output) {
        var args = new ArrayList<>(task.getTestArguments());
        args.add("--disable-ansi-colors");
        args.add("--disable-banner");
        return ConsoleLauncher.run(new PrintWriter(output), new PrintWriter(output), args.toArray(String[]::new))
            .getExitCode();
    }
}
