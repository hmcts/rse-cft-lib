package uk.gov.hmcts.rse;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;

public abstract class CftlibTestTask extends Test {
    public AuthMode authMode = AuthMode.AAT;

    public CftlibTestTask() {
        getJavaLauncher().set(getProject().getExtensions().getByType(JavaToolchainService.class)
            .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
        useJUnitPlatform();
        setMaxHeapSize("3g");
    }

    @Override
    public void executeTests() {
        if (getMaxParallelForks() != 1 || getForkEvery() != 0) {
            throw new GradleException(
                "cftlibTest requires maxParallelForks = 1 and forkEvery = 0 (shared service ports).");
        }
        CftlibForkOptions.configure(getProject(), this, authMode);
        // The consumer runs directly in Gradle's test worker, not a devtools restart classloader.
        systemProperty("spring.devtools.restart.enabled", false);
        systemProperty("junit.jupiter.execution.parallel.enabled", false);
        getSystemProperties().putIfAbsent("logging.file.name",
            new File(CftLibPlugin.cftlibBuildDir(getProject()).getAsFile(), "logs/libTest.log").getAbsolutePath());
        super.executeTests();
    }
}
