package uk.gov.hmcts.rse;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class CftLibPluginTest {

    @Test
    public void launchesSharedServicesOnJava21() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");

        CftlibExec task = project.getTasks().create("runServices", CftlibExec.class);

        assertThat(task.getJavaLauncher().get().getMetadata().getLanguageVersion().asInt()).isEqualTo(21);
    }

    @Test
    public void leavesXuiOutOfAProcessModeTestSuiteHoweverTheModeWasChosen() {
        // The readme shows the build script form first, and a task's dependencyMode is assigned
        // after the plugin has created the task, so the choice can only be read as it runs.
        CftlibExec task = task("cftlibTest");
        task.dependencyMode = DependencyMode.Process;

        task.resolveDependencies();

        assertThat(task.getEnvironment()).containsEntry("RSE_LIB_XUI", "off");
        assertThat(task.getEnvironment()).containsEntry("RSE_LIB_XUI_MO", "off");
        assertThat(task.getEnvironment()).containsEntry("COMPOSE_PROFILES", "");
    }

    @Test
    public void startsTheXuiContainersForADockerModeTestSuite() {
        // Compose started them for every cftlibTest before the profiles existed, and a
        // consumer's suite may drive the browser.
        CftlibExec task = task("cftlibTest");

        task.resolveDependencies();

        assertThat(task.getEnvironment()).doesNotContainKey("RSE_LIB_XUI");
        assertThat(task.getEnvironment()).containsEntry("COMPOSE_PROFILES", "xui,xui-mo");
    }

    @Test
    public void testSuiteThatAsksForXuiInProcessModeGetsIt() {
        CftlibExec task = task("cftlibTest");
        task.dependencyMode = DependencyMode.Process;
        task.environment("RSE_LIB_XUI", "process");

        task.resolveDependencies();

        assertThat(task.getEnvironment()).containsEntry("RSE_LIB_XUI", "process");
    }

    @Test
    public void processModeBootIsLeftWithItsXui() {
        // Only a test suite is given the default: bootWithCCD exists to be used.
        CftlibExec task = task("bootWithCCD");
        task.dependencyMode = DependencyMode.Process;

        task.resolveDependencies();

        assertThat(task.getEnvironment()).doesNotContainKey("RSE_LIB_XUI");
    }

    private CftlibExec task(String name) {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        CftlibExec task = project.getTasks().create(name, CftlibExec.class);
        // A task inherits this process's environment, which must not decide these tests.
        task.getEnvironment().keySet().removeAll(
            List.of("RSE_LIB_XUI", "RSE_LIB_XUI_MO", "RSE_LIB_DEPENDENCY_MODE", "COMPOSE_PROFILES"));
        return task;
    }

    @Test
    public void configuresLocalServiceBeforeItsJavaPluginIsApplied() {
        Project root = ProjectBuilder.builder().build();
        Project service = ProjectBuilder.builder().withName("wa-task-management-api").withParent(root).build();
        Project consumer = ProjectBuilder.builder().withName("et").withParent(root).build();

        consumer.getPlugins().apply(CftLibPlugin.class);
        service.getPlugins().apply("java");

        Task manifest = consumer.getTasks().getByName("writeManifestwa-task-management-api");
        assertThat(manifest.getTaskDependencies().getDependencies(manifest)
            .contains(service.getTasks().getByName("classes"))).isTrue();
    }

    @Test
    public void runsTheIdamSimulatorInLocalAuthModeOnly() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(CftLibPlugin.class);
        Task simulator = project.getTasks().getByName("writeManifestrse-idam-simulator");

        var boot = (CftlibExec) project.getTasks().getByName("bootWithCCD");
        assertThat(manifestNames(boot)).doesNotContain("rse-idam-simulator");
        assertThat(boot.getTaskDependencies().getDependencies(boot).contains(simulator)).isFalse();

        boot.authMode = AuthMode.Local;
        assertThat(manifestNames(boot)).contains("runtime", "rse-idam-simulator", "hostApplication");
        assertThat(boot.getTaskDependencies().getDependencies(boot).contains(simulator)).isTrue();

        var dump = (CftlibExec) project.getTasks().getByName("dumpCCDDefinitions");
        assertThat(manifestNames(dump)).contains("rse-idam-simulator");
    }

    private List<String> manifestNames(CftlibExec task) {
        var args = new ArrayList<>(task.getArgs());
        task.getArgumentProviders().forEach(provider -> provider.asArguments().forEach(args::add));
        return args.stream().map(arg -> new File(arg).getName()).collect(Collectors.toList());
    }
}
