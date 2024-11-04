package uk.gov.hmcts.rse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;

@RequiredArgsConstructor
@Getter
public class ManifestTask extends DefaultTask {
    @InputFiles
    public FileCollection classpath;
}
