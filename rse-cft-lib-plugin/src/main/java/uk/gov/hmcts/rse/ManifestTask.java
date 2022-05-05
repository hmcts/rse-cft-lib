package uk.gov.hmcts.rse;

import lombok.RequiredArgsConstructor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;

@RequiredArgsConstructor
public class ManifestTask extends DefaultTask {
  @Input
  public FileCollection classpath;
}
