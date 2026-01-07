package uk.gov.hmcts.rse;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import javax.inject.Inject;

import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;

public class ManifestTask extends DefaultTask {
    private final ConfigurableFileCollection classpath;

    private final Property<String> mainClass;

    private final ListProperty<String> args;

    private final RegularFileProperty outputFile;

    @Inject
    public ManifestTask(ObjectFactory objects) {
        this.classpath = objects.fileCollection();
        this.mainClass = objects.property(String.class);
        this.args = objects.listProperty(String.class);
        this.outputFile = objects.fileProperty();
    }

    @Classpath
    public ConfigurableFileCollection getClasspath() {
        return classpath;
    }

    @Input
    public Property<String> getMainClass() {
        return mainClass;
    }

    @Input
    public ListProperty<String> getArgs() {
        return args;
    }

    @OutputFile
    public RegularFileProperty getOutputFile() {
        return outputFile;
    }
}
