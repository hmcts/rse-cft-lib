package uk.gov.hmcts.rse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;

public class CftLibPlugin implements Plugin<Project> {

    final Map<String, String> projects = Map.of(
        "am-role-assignment-service-lib", "uk.gov.hmcts.reform.roleassignment.RoleAssignmentApplication",
        "ccd-data-store-api-lib", "uk.gov.hmcts.ccd.CoreCaseDataApplication",
        "definition-store-fat", "uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication",
        "user-profile-api-lib", "uk.gov.hmcts.ccd.UserProfileApplication"
    );
    private List<File> manifests = new ArrayList<>();
    private Set<Task> manifestTasks = new HashSet<>();

    public void apply(Project project) {
        project.getPlugins().apply("java");

        project.afterEvaluate(p -> {
          // CCD repos still rely on jcenter so add it as last repository.
          p.getRepositories().jcenter();
        });

        createSourceSets(project);
        createConfigurations(project);

        createManifestTasks(project);
        createBootWithCCDTask(project);
        createTestTask(project);
        surfaceSourcesToIDE(project);
    }

  /**
   *  Ensure the source/bytecode of the cft services is picked up by the IDE,
   *  since we resolve these dependencies in detached configurations that
   *  the IDE would not otherwise find.
   *
   *  We do this by creating an otherwise unused 'cftlibIDE' sourceset and associated
   *  dependency configuration.
   */
  private void surfaceSourcesToIDE(Project project) {
    project.getExtensions().getByType(SourceSetContainer.class)
      .create("cftlibIDE");
    var config = project.getConfigurations().getByName("cftlibIDEImplementation");
    config.getDependencies().addAll(Arrays.asList(
      libDependencies(project, projects.keySet().toArray(String[]::new))
    ));
  }

  private void createConfigurations(Project project) {
        project.getConfigurations().getByName("cftlibImplementation")
            .extendsFrom(project.getConfigurations().getByName("implementation"))
            .getDependencies().addAll(List.of(libDependencies(project, "app-runtime", "rse-cft-lib")));

        project.getConfigurations().getByName("cftlibRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("runtimeOnly"));
        project.getConfigurations().getByName("cftlibTestImplementation")
            .extendsFrom(project.getConfigurations().getByName("cftlibImplementation"))
            .getDependencies().addAll(List.of(
                project.getDependencies().create("org.junit.platform:junit-platform-console-standalone:1.8.2"),
                project.getDependencies().create("com.github.hmcts.rse-cft-lib:test-runner:" + getLibVersion(project))
            ));

        project.getConfigurations().getByName("cftlibTestRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("cftlibRuntimeOnly"));
    }

    private void createSourceSets(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        s.add(s.create("cftlib", x -> {
            var main = s.getByName("main").getOutput();
            x.setCompileClasspath(main.plus(x.getCompileClasspath()));
            x.setRuntimeClasspath(main.plus(x.getRuntimeClasspath()));
        }));

        s.add(s.create("cftlibTest", x -> {
            var cftlib = s.getByName("cftlib").getOutput();
            var main = s.getByName("main").getOutput();

            x.setCompileClasspath(main.plus(cftlib).plus(x.getCompileClasspath()));
            x.setRuntimeClasspath(main.plus(cftlib).plus(x.getRuntimeClasspath()));
        }));
    }

  private void createBootWithCCDTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlib");

        var exec = createRunTask(project, "bootWithCCD");
        var file = getBuildDir(project).file("application").getAsFile();
        exec.doFirst(t -> {
          JavaExec e = (JavaExec) project.getTasks().getByName("bootRun");
          String clazz = "";

          if (e.getMainClass().isPresent()) {
            clazz = e.getMainClass().get();
          } else {
            clazz = project.getProperties().get("mainClassName").toString();
          }

            var args = "--rse.lib.service_name=" + project.getName();
            writeManifest(project, lib.getRuntimeClasspath(), clazz, file, args);
        });


        exec.dependsOn("cftlibClasses");

        if (null != project.getTasks().findByName("bootRunMainClassName")) {
          exec.dependsOn(project.getTasks().getByName("bootRunMainClassName"));
        }
        exec.args(file);
    }

    private void createTestTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlibTest");

        var exec = createRunTask(project, "cftlibTest");
        var file = getBuildDir(project).file("libTest").getAsFile();
        var app = createManifestTask(project, "manifestTest", lib.getRuntimeClasspath(), "org.junit.platform.console.ConsoleLauncher", file, "--select-package=uk.gov.hmcts");
        exec.dependsOn(app);
        exec.dependsOn("cftlibClasses");
        exec.dependsOn("cftlibTestClasses");
        exec.args(file);
        exec.environment("RSE_LIB_STUB_AUTH_OUTBOUND", "true");

    }

    private void createManifestTasks(Project project) {
        {
            // Runtime is always the first manifest
            var file = getBuildDir(project).file("runtime").getAsFile();
            Configuration classpath = project.getConfigurations().detachedConfiguration(
            libDependencies(project, "runtime"));
            var task = createManifestTask(project, "writeManifestRuntime", classpath, "uk.gov.hmcts.rse.ccd.lib.Application", file);
            manifestTasks.add(task);
            manifests.add(file);
        }

        for(var e: projects.entrySet()) {
            var file = getBuildDir(project).file(e.getKey()).getAsFile();
            var args = Lists.newArrayList(
                "--rse.lib.service_name=" + e.getKey()
            );
            if (e.getKey().equals("ccd-data-store-api-lib")) {
                args.add("--idam.client.secret=${IDAM_OAUTH2_DATA_STORE_CLIENT_SECRET:}");
            }
            manifestTasks.add(createCFTManifestTask(project, e.getKey(), e.getValue(), file, args.toArray(String[]::new)));
            manifests.add(file);
        }
    }

    private Task createCFTManifestTask(Project project, String depName, String mainClass, File file, String... args) {
        Configuration classpath = project.getConfigurations().detachedConfiguration(
            libDependencies(project, depName, "injected"));
        return createManifestTask(project, "writeManifest" + depName, classpath, mainClass, file, args);
    }

    private Task createManifestTask(Project project, String name, FileCollection configuration, String mainClass, File file, String... args) {
        return project.getTasks().create(name)
            .doFirst(x -> {
                writeManifest(project, configuration, mainClass, file, args);
            });
    }

    @SneakyThrows
    private void writeManifest(Project project, FileCollection classpath, String mainClass, File file, String... args) {
        var deps = new ArrayList<String>();
        for (File f : classpath) {
            deps.add(f.getAbsolutePath());
        }

        getBuildDir(project).getAsFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(mainClass + " " + Joiner.on(" ").join(args));
            for (var path : deps) {
                writer.println(path);
            }
        }
    }

    private String getLibVersion(Project project) {
        return project.getBuildscript().getConfigurations()
            .getByName("classpath")
            .getDependencies()
            .stream().
            filter(x -> x.getGroup().equals("com.github.hmcts.rse-cft-lib")
                && x.getName().equals("com.github.hmcts.rse-cft-lib.gradle.plugin"))
            .findFirst()
            .map(Dependency::getVersion)
            .orElse("DEV-SNAPSHOT");
    }

    private LibRunnerTask createRunTask(Project project, String name) {
        LibRunnerTask j = project.getTasks().create(name, LibRunnerTask.class);
        j.setMain("uk.gov.hmcts.rse.ccd.lib.LibRunner");


        j.doFirst(x -> {
            // Resolve the configuration as a detached configuration for isolation from
            // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
            j.classpath(project.getConfigurations().detachedConfiguration(libDependencies(project, "rse-cft-lib")));
        });

        j.args(manifests);
        j.dependsOn(manifestTasks);
        return j;
    }


    Dependency[] libDependencies(Project project, String... libDeps) {
        return Arrays.stream(libDeps)
            .map(d -> project.getDependencies().create("com.github.hmcts.rse-cft-lib:" + d + ":" + getLibVersion(project)))
            .toArray(Dependency[]::new);
    }

    Directory getBuildDir(Project project) {
        return project.getLayout().getBuildDirectory().dir("cftlib").get();
    }
}
