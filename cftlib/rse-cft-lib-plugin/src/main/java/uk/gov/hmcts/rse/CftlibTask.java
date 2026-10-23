package uk.gov.hmcts.rse;

import org.gradle.api.Task;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.util.Map;

/** Common configuration for CFTLib execution and test tasks. */
public interface CftlibTask extends Task, JavaForkOptions {
    // Match the native fork tasks: process launch details are not standalone task inputs.
    @Override
    @Internal
    Map<String, Object> getEnvironment();

    @Override
    @Internal
    String getExecutable();

    @Override
    @Internal
    File getWorkingDir();

    @Input
    AuthMode getAuthMode();

    void setAuthMode(AuthMode authMode);
}
