package uk.gov.hmcts.rse;

import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;

public abstract class CftlibTestTask extends Test implements CftlibTask {
    public AuthMode authMode = AuthMode.AAT;

    public CftlibTestTask() {
        getJavaLauncher().set(getProject().getExtensions().getByType(JavaToolchainService.class)
            .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
        useJUnitPlatform();
        setMaxHeapSize("3g");
    }

    @Override
    public AuthMode getAuthMode() {
        return authMode;
    }

    @Override
    public void setAuthMode(AuthMode authMode) {
        this.authMode = authMode;
    }

    @Override
    public void executeTests() {
        if (getMaxParallelForks() != 1 || getForkEvery() != 0) {
            getLogger().warn("{} shares service ports and in-memory state; overriding maxParallelForks={} "
                + "and forkEvery={} with maxParallelForks=1 and forkEvery=0.",
                getPath(), getMaxParallelForks(), getForkEvery());
            setMaxParallelForks(1);
            setForkEvery(0);
        }
        CftlibForkOptions.configure(getProject(), this, authMode);
        // The consumer runs directly in Gradle's test worker, not a devtools restart classloader.
        systemProperty("spring.devtools.restart.enabled", false);
        getSystemProperties().putIfAbsent("logging.file.name",
            new File(CftLibPlugin.cftlibBuildDir(getProject()).getAsFile(), "logs/libTest.log").getAbsolutePath());
        super.executeTests();
    }
}
