package uk.gov.hmcts.rse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.SneakyThrows;
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
            x.getCompileClasspath().plus(main);
            x.getRuntimeClasspath().plus(main);
        }));

        project.getConfigurations().getByName("cftlibImplementation")
            .extendsFrom(project.getConfigurations().getByName("implementation"));

        project.getConfigurations().getByName("cftlibRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("runtimeOnly"));

        s.add(s.create("cftlibTest", x -> {
            var main = s.getByName("cftlib").getOutput();
            x.getCompileClasspath().plus(main);
            x.getRuntimeClasspath().plus(main);
        }));

        project.getConfigurations().getByName("cftlibTestImplementation")
            .extendsFrom(project.getConfigurations().getByName("cftlibImplementation"));

        project.getConfigurations().getByName("cftlibTestRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("cftlibRuntimeOnly"));

        project.getConfigurations().getByName("cftlibTestImplementation")
                .getDependencies().add(project.getDependencies().create("org.junit.platform:junit-platform-console-standalone:1.8.2"));

        createManifestTasks(project);
        createBootWithCCDTask(project);
        createTestTask(project);
    }

    private void createBootWithCCDTask(Project project) {
        var exec = createRunTask(project, "bootWithCCD", "cftlibImplementation");
    }

    private void createTestTask(Project project) {
        var exec = createRunTask(project, "cftlibTest", "cftlibImplementation");
        exec.setMain("org.junit.platform.console.ConsoleLauncher");
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
            manifestTasks.add(createCFTManifestTask(project, e.getKey(), e.getValue(), file));
            manifests.add(file);
        }
    }

    private JavaExec createRunTask(Project project, String name, String configurationName) {
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

        j.environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
            "https://idam-web-public.aat.platform.hmcts.net/o");


        j.jvmArgs("-Xverify:none");
        j.jvmArgs("-XX:TieredStopAtLevel=1");

        // This needs to happen after evaluation so the lib version is set in the build.gradle.
        j.doFirst(x -> {
            // Resolve the configuration as a detached configuration for isolation from
            // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
            // Copy only the direct dependencies, ie. the lib control plane.
            var deps = project.getConfigurations().getByName("cftlibImplementation")
                .getDependencies()
                .toArray(Dependency[]::new);

            Configuration classpath = project.getConfigurations().detachedConfiguration(deps);
            j.classpath(classpath);
        });

        j.args(manifests);
        j.dependsOn(manifestTasks);
        return j;
    }

    private Task createCFTManifestTask(Project project, String depName, String mainClass, File file) {
        return project.task("writeManifest" + depName)
            .doFirst(x -> {
                Configuration classpath = configurationWithDependency(project, depName);
                writeManifest(project, classpath, mainClass, file);
            });
    }

    private Configuration configurationWithDependency(Project project, String name) {
        return project.getConfigurations().detachedConfiguration(
            project.getDependencies().create("com.github.hmcts:" + name + ":" + getLibVersion(project)),
            project.getDependencies().create("com.github.hmcts:injected:" + getLibVersion(project))
        );
    }

    @SneakyThrows
    private void writeManifest(Project project, Configuration classpath, String mainClass, File file) {
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
