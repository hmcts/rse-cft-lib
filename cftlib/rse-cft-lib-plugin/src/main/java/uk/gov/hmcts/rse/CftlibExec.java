package uk.gov.hmcts.rse;

import org.gradle.api.tasks.JavaExec;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

public class CftlibExec extends JavaExec implements CftlibTask {
    public AuthMode authMode = AuthMode.AAT;

    public CftlibExec() {
        getJavaLauncher().set(getProject().getExtensions().getByType(JavaToolchainService.class)
            .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
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
    public void exec() {
        CftlibForkOptions.configure(getProject(), this, authMode);
        // Enable live reload for bootWithCCD's isolated application classloader.
        systemProperty("spring.devtools.restart.enabled", true);
        super.exec();
    }
}
