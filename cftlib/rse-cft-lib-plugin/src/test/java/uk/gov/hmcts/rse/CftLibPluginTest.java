package uk.gov.hmcts.rse;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

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
    public void configuresAllCftlibTaskTypesIncludingLaterRegistrations() {
        Project project = ProjectBuilder.builder().build();
        project.getExtensions().getExtraProperties().set("cftlib.nativeTests", true);
        project.getPlugins().apply(CftLibPlugin.class);
        project.getTasks().withType(CftlibTask.class).configureEach(task -> {
            task.setAuthMode(AuthMode.Local);
            task.environment("SHARED_SETTING", "shared");
            task.jvmArgs("-XX:TieredStopAtLevel=1");
            task.systemProperty("shared.setting", "shared");
        });
        var later = project.getTasks().register("customCftlib", CftlibExec.class).get();
        for (String name : java.util.List.of("bootWithCCD", "cftlibTest", "dumpCCDDefinitions", later.getName())) {
            CftlibTask task = (CftlibTask) project.getTasks().getByName(name);
            assertThat(task.getAuthMode()).isEqualTo(AuthMode.Local);
            assertThat(task.getEnvironment()).containsEntry("SHARED_SETTING", "shared");
            assertThat(task.getJvmArgs()).contains("-XX:TieredStopAtLevel=1");
            assertThat(task.getSystemProperties()).containsEntry("shared.setting", "shared");
        }
        var test = (CftlibTestTask) project.getTasks().getByName("cftlibTest");
        test.environment("SHARED_SETTING", "test-specific");
        assertThat(test.getEnvironment()).containsEntry("SHARED_SETTING", "test-specific");
        assertThat(later.getEnvironment()).containsEntry("SHARED_SETTING", "shared");
        // Existing selectors and direct field access remain compatible for execution tasks.
        assertThat(project.getTasks().withType(CftlibExec.class)).extracting(Task::getName)
            .contains(later.getName()).doesNotContain(test.getName());
        assertThat(later.authMode).isEqualTo(AuthMode.Local);
    }

    @Test
    public void keepsLegacyRunnerSelectorsAndManifestEnabledByDefault() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(CftLibPlugin.class);
        project.getTasks().withType(CftlibExec.class).configureEach(task -> {
            task.authMode = AuthMode.Local;
            task.environment("LEGACY_SETTING", "preserved");
        });
        var task = project.getTasks().getByName("cftlibTest");
        assertThat(task).isInstanceOf(CftlibExec.class);
        assertThat(((CftlibExec) task).authMode).isEqualTo(AuthMode.Local);
        assertThat(((CftlibExec) task).getEnvironment()).containsEntry("LEGACY_SETTING", "preserved");
        var manifest = project.getTasks().getByName("manifestTest");
        assertThat(manifest).isInstanceOf(ManifestTask.class);
        assertThat(manifest.getEnabled()).isTrue();
        assertThat(manifest.getOutputs().getFiles().getSingleFile().getName()).isEqualTo("libTest");
    }

}
