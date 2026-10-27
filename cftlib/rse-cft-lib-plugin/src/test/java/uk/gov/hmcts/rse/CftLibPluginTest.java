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
