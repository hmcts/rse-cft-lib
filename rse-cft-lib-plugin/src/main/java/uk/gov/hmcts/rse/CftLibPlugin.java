package uk.gov.hmcts.rse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import lombok.SneakyThrows;
import org.apache.tools.ant.taskdefs.Java;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;

public class CftLibPlugin implements Plugin<Project> {

  public void apply(Project project) {

      // Create the configurations first so they are available to the build script.
      project.getConfigurations().create("cftlibImplementation");
      project.getConfigurations().create("cftlibRuntimeOnly");

      project.beforeEvaluate(p -> {
          // SourceSetContainer now available.
          SourceSetContainer s = p.getExtensions().getByType(SourceSetContainer.class);
          s.create("cftlib", x -> {
              var main = s.getByName("main").getOutput();
              x.getCompileClasspath().plus(main);
              x.getRuntimeClasspath().plus(main);
          });

          p.getConfigurations().getByName("cftlibImplementation")
              .extendsFrom(p.getConfigurations().getByName("implementation"));

          p.getConfigurations().getByName("cftlibRuntimeOnly")
              .extendsFrom(p.getConfigurations().getByName("runtimeOnly"));
      });

      final var file = project.getLayout().getBuildDirectory().file("manifest").get().getAsFile();
      var writeManifest = project.task("writeManifest")
          .doFirst(x -> {
              writeManifest(project, file);
          });

      JavaExec j = project.getTasks().create("bootWithCCD", JavaExec.class);
      j.dependsOn(writeManifest);
      j.setMain("uk.gov.hmcts.rse.ccd.lib.LibRunner");

      j.environment("USER_PROFILE_DB_PORT", 6432);
      j.environment("USER_PROFILE_DB_USERNAME", "postgres");
      j.environment("USER_PROFILE_DB_PASSWORD", "postgres");
      j.environment("USER_PROFILE_DB_NAME", "userprofile");
      j.environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");
      j.args(file.getAbsolutePath());

      j.doFirst(x -> {
          // Resolve the configuration as a detached configuration for isolation from
          // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
          var deps = project.getConfigurations().getByName("cftlibImplementation")
              .getDependencies()
              .toArray(Dependency[]::new);

          Configuration classpath = project.getConfigurations().detachedConfiguration(deps);
          j.classpath(classpath);
      });
  }


    @SneakyThrows
    private void writeManifest(Project project, File file) {
        Configuration classpath = project.getConfigurations().detachedConfiguration(
            project.getDependencies().create("com.github.hmcts:user-profile-api-lib:" + getLibVersion(project))
        );

        project.getLayout().getBuildDirectory().getAsFile().get().mkdir();
        file.createNewFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (ResolvedArtifact resolvedArtifact : classpath.getResolvedConfiguration().getResolvedArtifacts()) {
                writer.println(resolvedArtifact.getFile().getAbsolutePath());
            }
        }
    }

    private String getLibVersion(Project project) {
        var dep = project.getConfigurations()
            .getByName("cftlibImplementation")
            .getDependencies()
            .stream().
             filter(x -> x.getGroup().equals("com.github.hmcts")
                 && x.getName().equals("rse-cft-lib"))
            .findFirst()
            .orElseThrow();
        return dep.getVersion();
    }

}
