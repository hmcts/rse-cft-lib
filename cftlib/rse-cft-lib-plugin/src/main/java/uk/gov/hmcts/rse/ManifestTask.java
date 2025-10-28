package uk.gov.hmcts.rse;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public class ManifestTask extends DefaultTask {
    private FileCollection classpath;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }
}
