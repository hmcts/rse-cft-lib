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
import lombok.SneakyThrows;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
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

        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        s.add(s.create("cftlib", x -> {
            var main = s.getByName("main").getOutput();
            x.setCompileClasspath(x.getCompileClasspath().plus(main));
            x.setRuntimeClasspath(x.getRuntimeClasspath().plus(main));
        }));

        project.getConfigurations().getByName("cftlibImplementation")
            .extendsFrom(project.getConfigurations().getByName("implementation"));

        project.afterEvaluate(x -> {
            project.getConfigurations().getByName("cftlibImplementation")
                .getDependencies().addAll(List.of(
                    project.getDependencies().create("com.github.hmcts.rse-cft-lib:app-runtime:" + getLibVersion(project)),
                    project.getDependencies().create("com.github.hmcts.rse-cft-lib:rse-cft-lib:" + getLibVersion(project))
                ));
        });

        project.getConfigurations().getByName("cftlibRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("runtimeOnly"));

        s.add(s.create("cftlibTest", x -> {
            var cftlib = s.getByName("cftlib").getOutput();
            var main = s.getByName("main").getOutput();

            x.setCompileClasspath(x.getCompileClasspath().plus(cftlib).plus(main));
            x.setRuntimeClasspath(x.getRuntimeClasspath().plus(cftlib).plus(main));
        }));

        createTestSourceSet(project);

        createManifestTasks(project);
        createBootWithCCDTask(project);
        createTestTask(project);
    }

    private void createTestSourceSet(Project project) {
        var impl = project.getConfigurations().getByName("cftlibTestImplementation");
        impl.extendsFrom(project.getConfigurations().getByName("cftlibImplementation"));

        project.getConfigurations().getByName("cftlibTestRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("cftlibRuntimeOnly"));

        impl.getDependencies().add(project.getDependencies().create("org.junit.platform:junit-platform-console-standalone:1.8.2"));
        // Wait until build script evaluation to get lib version.
        project.afterEvaluate(x -> impl.getDependencies().add(project.getDependencies().create("com.github.hmcts.rse-cft-lib:test-runner:" + getLibVersion(project))));
    }

    private void createBootWithCCDTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlib");

        var exec = createRunTask(project, "bootWithCCD");
        var file = project.getLayout().getBuildDirectory().file("application").get().getAsFile();
        exec.doFirst(t -> {
            JavaExec e = (JavaExec) project.getTasks().getByName("bootRun");
            writeManifest(project, lib.getRuntimeClasspath(), e.getMainClass().get(), file);
        });

        exec.dependsOn("cftlibClasses");
        exec.dependsOn(project.getTasks().getByName("bootRunMainClassName"));
        exec.args(file);
    }

    private void createTestTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlibTest");

        var exec = createRunTask(project, "cftlibTest");
        var file = project.getLayout().getBuildDirectory().file("libTest").get().getAsFile();
        var app = createManifestTask(project, "manifestTest", lib.getRuntimeClasspath(), "org.junit.platform.console.ConsoleLauncher", file, "--select-package=uk.gov.hmcts.libconsumer");
        exec.dependsOn(app);
        exec.dependsOn("cftlibClasses");
        exec.dependsOn("cftlibTestClasses");
        exec.args(file);
        exec.environment("RSE_LIB_STUB_S2S", "true");

    }

    private void createManifestTasks(Project project) {
        {
            var file = project.getLayout().getBuildDirectory().file("runtime")
                .get().getAsFile();
            manifestTasks.add(createCFTManifestTask(project, "runtime", "uk.gov.hmcts.rse.ccd.lib.ComposeRunner", file));
            manifests.add(file);
        }

        for(var e: projects.entrySet()) {
            var file = project.getLayout().getBuildDirectory().file(e.getKey())
                .get().getAsFile();
            String[] args = new String[0];
            if (e.getKey().equals("ccd-data-store-api-lib")) {
                args = new String[] {
                    "--idam.client.secret=${IDAM_OAUTH2_DATA_STORE_CLIENT_SECRET:}"
                };
            }
            manifestTasks.add(createCFTManifestTask(project, e.getKey(), e.getValue(), file, args));
            manifests.add(file);
        }
    }

    private Task createCFTManifestTask(Project project, String depName, String mainClass, File file, String... args) {
        return project.task("writeManifest" + depName)
            .doFirst(x -> {
                Configuration classpath = cftConfiguration(project, depName);
                writeManifest(project, classpath, mainClass, file, args);
            });
    }

    private Task createManifestTask(Project project, String name, FileCollection configuration, String mainClass, File file, String... args) {
        return project.task(name)
            .doFirst(x -> {
                writeManifest(project, configuration, mainClass, file, args);
            });
    }

    private Configuration cftConfiguration(Project project, String name) {
        return configuration(project,
            "com.github.hmcts.rse-cft-lib:" + name + ":" + getLibVersion(project),
            "com.github.hmcts.rse-cft-lib:injected:" + getLibVersion(project)
        );
    }

    private Configuration configuration(Project project, String... dependencies) {
        var deps = Arrays.stream(dependencies).map(
            x -> project.getDependencies().create(x)
        ).toArray(Dependency[]::new);

        return project.getConfigurations().detachedConfiguration(deps);
    }

    @SneakyThrows
    private void writeManifest(Project project, FileCollection classpath, String mainClass, File file, String... args) {
        var deps = new ArrayList<String>();
        for (File f : classpath) {
            deps.add(f.getAbsolutePath());
        }
        Collections.sort(deps);

        project.getLayout().getBuildDirectory().getAsFile().get().mkdir();
        file.createNewFile();
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

    private JavaExec createRunTask(Project project, String name) {
        JavaExec j = project.getTasks().create(name, JavaExec.class);
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

        j.environment("DEFINITION_STORE_DB_PORT", 6432);
        j.environment("DEFINITION_STORE_DB_USERNAME", "postgres");
        j.environment("DEFINITION_STORE_DB_PASSWORD", "postgres");
        j.environment("DEFINITION_STORE_DB_NAME", "definitionstore");

        j.environment("ROLE_ASSIGNMENT_DB_HOST", "localhost");
        j.environment("ROLE_ASSIGNMENT_DB_PORT", "6432");
        j.environment("ROLE_ASSIGNMENT_DB_NAME", "am");
        j.environment("ROLE_ASSIGNMENT_DB_USERNAME", "postgres");
        j.environment("ROLE_ASSIGNMENT_DB_PASSWORD", "postgres");

        j.environment("SEARCH_ELASTIC_HOSTS", "http://localhost:9200");
        j.environment("SEARCH_ELASTIC_DATA_HOSTS", "http://localhost:9200");
        j.environment("ELASTICSEARCH_ENABLED", "true");
        j.environment("ELASTICSEARCH_FAILIMPORTIFERROR", "true");

        // Allow more time for definitions to import to reduce test flakeyness
        j.environment("CCD_TX-TIMEOUT_DEFAULT", "120");


        j.environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
            "https://idam-web-public.aat.platform.hmcts.net/o");

//        j.environment("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY", "DEBUG");

        setJvmArgs(j);

        // This needs to happen after evaluation so the lib version is set in the build.gradle.
        j.doFirst(x -> {
            // Resolve the configuration as a detached configuration for isolation from
            // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
            Configuration classpath = configuration(project, "com.github.hmcts.rse-cft-lib:rse-cft-lib:" + getLibVersion(project));
            j.classpath(classpath);
        });

        j.args(manifests);
        j.dependsOn(manifestTasks);
        return j;
    }

    void setJvmArgs(JavaExec j) {
        j.jvmArgs("-Xverify:none");
        j.jvmArgs("-XX:TieredStopAtLevel=1");

        // Required by Access Management for Java 17.
        // https://github.com/x-stream/xstream/issues/101
        List.of("java.lang", "java.util", "java.lang.reflect", "java.text", "java.awt.font").forEach(x -> {
            j.jvmArgs("--add-opens", "java.base/" + x + "=ALL-UNNAMED");
        });
        j.jvmArgs("--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED");
        j.jvmArgs("-XX:ReservedCodeCacheSize=64m");
    }

}
