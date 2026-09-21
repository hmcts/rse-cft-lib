package uk.gov.hmcts.rse;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CftLibPluginTest {

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
}
