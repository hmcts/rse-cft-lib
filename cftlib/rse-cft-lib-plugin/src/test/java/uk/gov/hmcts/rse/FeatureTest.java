package uk.gov.hmcts.rse;

import org.gradle.api.InvalidUserDataException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FeatureTest {

    @Test
    public void combinesTheTasksFeaturesWithThoseRequestedForOneRun() {
        assertThat(Feature.resolve(List.of("camunda"), Map.of("RSE_LIB_FEATURES", " servicebus,camunda ")))
            .containsExactly(Feature.camunda, Feature.servicebus);
        assertThat(Feature.resolve(List.of(), Map.of())).isEmpty();
    }

    @Test
    public void addsTheFeaturesAFeatureNeeds() {
        assertThat(Feature.resolve(List.of("hmc", "wa")))
            .containsExactly(Feature.hmc, Feature.servicebus, Feature.wa, Feature.camunda);
    }

    @Test
    public void rejectsAnUnknownFeature() {
        assertThatThrownBy(() -> Feature.resolve(List.of("rabbitmq")))
            .isInstanceOf(InvalidUserDataException.class)
            .hasMessageContaining("rabbitmq");
    }

    @Test
    public void readsFeaturesFromTheTask() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        var task = project.getTasks().create("bootWithCCD", CftlibExec.class);

        task.features("servicebus");
        task.environment("RSE_LIB_FEATURES", "camunda");

        assertThat(task.getResolvedFeatures()).containsExactly(Feature.servicebus, Feature.camunda);
    }

    @Test
    public void rejectsFeaturesThatNeedLocalAuthWithoutIt() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        var task = project.getTasks().create("bootWithCCD", CftlibExec.class);
        task.features("wa", "servicebus");

        assertThatThrownBy(task::checkFeaturesSupportAuthMode)
            .isInstanceOf(InvalidUserDataException.class)
            .hasMessageContaining("The wa feature needs authMode = AuthMode.Local");

        task.authMode = AuthMode.Local;
        task.checkFeaturesSupportAuthMode();
    }
}
