package uk.gov.hmcts.rse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;

@RequiredArgsConstructor
public class ManifestTask extends DefaultTask {
  public FileCollection classpath;
}
