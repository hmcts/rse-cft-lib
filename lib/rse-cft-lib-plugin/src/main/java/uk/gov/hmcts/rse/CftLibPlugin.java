package uk.gov.hmcts.rse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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

    final Map<String, String> projects = Map.of(
        "am-role-assignment-service-lib", "uk.gov.hmcts.reform.roleassignment.RoleAssignmentApplication",
        "ccd-data-store-api-lib", "uk.gov.hmcts.ccd.CoreCaseDataApplication",
//        "case-definition-store-api-lib", "uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication"
        "user-profile-api-lib", "uk.gov.hmcts.ccd.UserProfileApplication"
    );

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

      JavaExec j = project.getTasks().create("bootWithCCD", JavaExec.class);
      j.setMain("uk.gov.hmcts.rse.ccd.lib.LibRunner");

      j.environment("USER_PROFILE_DB_PORT", 6432);
      j.environment("USER_PROFILE_DB_USERNAME", "postgres");
      j.environment("USER_PROFILE_DB_PASSWORD", "postgres");
      j.environment("USER_PROFILE_DB_NAME", "userprofile");
      j.environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");

      j.environment("DATA_STORE_DB_PORT", 6432);
      j.environment("DATA_STORE_DB_USERNAME", "postgres");
      j.environment("DATA_STORE_DB_PASSWORD", "postgres");
      j.environment("DATA_STORE_DB_NAME", "datastore");

      j.environment("ROLE_ASSIGNMENT_DB_HOST", "localhost");
      j.environment("ROLE_ASSIGNMENT_DB_PORT", "6432");
      j.environment("ROLE_ASSIGNMENT_DB_NAME", "am");
      j.environment("ROLE_ASSIGNMENT_DB_USERNAME", "postgres");
      j.environment("ROLE_ASSIGNMENT_DB_PASSWORD", "postgres");

      j.environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
          "https://idam-web-public.aat.platform.hmcts.net/o");

      {
          var file = project.getLayout().getBuildDirectory().file("runtime")
              .get().getAsFile();
          j.dependsOn(createManifestTask(project, "runtime", "uk.gov.hmcts.rse.ccd.lib.ComposeRunner", file));
          j.args(file.getAbsolutePath());
      }


      for(var e: projects.entrySet()) {
          var file = project.getLayout().getBuildDirectory().file(e.getKey())
                  .get().getAsFile();
          j.dependsOn(createManifestTask(project, e.getKey(), e.getValue(), file));
          j.args(file.getAbsolutePath());
      }

      // This needs to happen after evaluation so the lib version is set in the build.gradle.
      j.doFirst(x -> {
          // Resolve the configuration as a detached configuration for isolation from
          // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
          var deps = project.getConfigurations().getByName("cftlibImplementation")
              .getDependencies()
              .toArray(Dependency[]::new);

          Configuration classpath = project.getConfigurations().detachedConfiguration(deps);
          j.classpath(classpath);
      });

      j.jvmArgs("-Xverify:none");
      j.jvmArgs("-XX:TieredStopAtLevel=1");
  }

  private Task createManifestTask(Project project, String depName, String mainClass, File file) {
      return project.task("writeManifest" + depName)
          .doFirst(x -> {
              writeManifest(project, depName, mainClass, file);
          });
  }

    @SneakyThrows
    private void writeManifest(Project project, String name, String mainClass, File file) {
        Configuration classpath = project.getConfigurations().detachedConfiguration(
            project.getDependencies().create("com.github.hmcts:" + name + ":" + getLibVersion(project)),
            project.getDependencies().create("com.github.hmcts:injected:" + getLibVersion(project))
        );

        var deps = new ArrayList<String>();
        for (ResolvedArtifact resolvedArtifact : classpath.getResolvedConfiguration().getResolvedArtifacts()) {
            deps.add(resolvedArtifact.getFile().getAbsolutePath());
        }
        Collections.sort(deps);

        project.getLayout().getBuildDirectory().getAsFile().get().mkdir();
        file.createNewFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(mainClass);
            for (var path : deps) {
                writer.println(path);
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
