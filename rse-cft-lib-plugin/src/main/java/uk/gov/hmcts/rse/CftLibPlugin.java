package uk.gov.hmcts.rse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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


        createSourceSets(project);
        createConfigurations(project);

        createManifestTasks(project);
        createBootWithCCDTask(project);
        createTestTask(project);
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
            x.setCompileClasspath(x.getCompileClasspath().plus(main));
            x.setRuntimeClasspath(x.getRuntimeClasspath().plus(main));
        }));

        s.add(s.create("cftlibTest", x -> {
            var cftlib = s.getByName("cftlib").getOutput();
            var main = s.getByName("main").getOutput();

            x.setCompileClasspath(x.getCompileClasspath().plus(cftlib).plus(main));
            x.setRuntimeClasspath(x.getRuntimeClasspath().plus(cftlib).plus(main));
        }));
    }

    private void createBootWithCCDTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlib");

        var exec = createRunTask(project, "bootWithCCD");
        var file = getBuildDir(project).file("application").getAsFile();
        exec.doFirst(t -> {
            JavaExec e = (JavaExec) project.getTasks().getByName("bootRun");

            var name = "--rse.lib.service_name=" + project.getName();
            writeManifest(project, lib.getRuntimeClasspath(), e.getMainClass().get(), file, name);
        });

        exec.dependsOn("cftlibClasses");
        exec.dependsOn(project.getTasks().getByName("bootRunMainClassName"));
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
        return project.task(name)
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
        Collections.sort(deps);

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

        setRequiredJvmArgs(j);

        j.doFirst(x -> {
            // Resolve the configuration as a detached configuration for isolation from
            // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
            j.classpath(project.getConfigurations().detachedConfiguration(libDependencies(project, "rse-cft-lib")));
        });

        j.args(manifests);
        j.dependsOn(manifestTasks);
        return j;
    }

    void setRequiredJvmArgs(JavaExec j) {
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

    Dependency[] libDependencies(Project project, String... libDeps) {
        return Arrays.stream(libDeps)
            .map(d -> project.getDependencies().create("com.github.hmcts.rse-cft-lib:" + d + ":" + getLibVersion(project)))
            .toArray(Dependency[]::new);
    }

    Directory getBuildDir(Project project) {
        return project.getLayout().getBuildDirectory().dir("cftlib").get();
    }
}
