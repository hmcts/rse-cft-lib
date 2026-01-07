package uk.gov.hmcts.rse;

import lombok.RequiredArgsConstructor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;

@RequiredArgsConstructor
public class ManifestTask extends DefaultTask {
    public FileCollection classpath;
}
