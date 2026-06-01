package uk.gov.hmcts.rse;

import java.io.FileWriter;
import java.io.PrintWriter;

import com.google.common.base.Joiner;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class ManifestTask extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClassName();

    @Input
    public abstract ListProperty<String> getManifestArgs();

    @OutputFile
    public abstract RegularFileProperty getManifestFile();

    @TaskAction
    @SneakyThrows
    public void writeManifest() {
        var file = getManifestFile().get().getAsFile();
        file.getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(getMainClassName().get() + " " + Joiner.on(" ").join(getManifestArgs().get()));
            for (var classpathFile : getClasspath()) {
                writer.println(classpathFile.getAbsolutePath());
            }
        }
    }
}
