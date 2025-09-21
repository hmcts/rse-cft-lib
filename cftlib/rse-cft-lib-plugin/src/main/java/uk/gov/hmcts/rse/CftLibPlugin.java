package uk.gov.hmcts.rse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;

public class CftLibPlugin implements Plugin<Project> {

    final Map<Service, String> projects = Map.of(
            Service.amRoleAssignmentService, "uk.gov.hmcts.reform.roleassignment.RoleAssignmentApplication",
            Service.ccdDataStoreApi, "uk.gov.hmcts.ccd.CoreCaseDataApplication",
            Service.ccdDefinitionStoreApi, "uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication",
            Service.ccdUserProfileApi, "uk.gov.hmcts.ccd.UserProfileApplication",
            Service.aacManageCaseAssignment, "uk.gov.hmcts.reform.managecase.Application",
            Service.ccdCaseDocumentAmApi, "uk.gov.hmcts.reform.ccd.documentam.Application",
            Service.dgDocassemblyApi, "uk.gov.hmcts.reform.dg.docassembly.Application"
    );
    private final List<File> manifests = new ArrayList<>();
    private final List<ManifestTask> manifestTasks = Lists.newArrayList();

    public void apply(Project project) {
        project.getPlugins().apply("java");

        createSourceSets(project);
        createConfigurations(project);

        registerDependencyRepositories(project);
        createManifestTasks(project);
        var manifestTask = createHostAppManifestTask(project);
        createBootWithCCDTask(project, manifestTask, "bootWithCCD");
        var dumpDefinitions = createBootWithCCDTask(project, manifestTask, "dumpCCDDefinitions");
        dumpDefinitions.environment("RSE_LIB_DUMP_DEFINITIONS", "true");
        dumpDefinitions.authMode = AuthMode.Local;
        dumpDefinitions.getOutputs().upToDateWhen(x -> false);

        configureJacoco(project, createTestTask(project));
        surfaceSourcesToIDE(project);
        createCftlibJarTask(project);
    }

    /**
     * Apply Jacoco to the task if jacoco plugin is applied to the project.
     */
    private void configureJacoco(Project p, CftlibExec task) {
        if (p.getPlugins().hasPlugin(JacocoPlugin.class)) {
            var e = p.getExtensions().getByType(JacocoPluginExtension.class);
            e.applyTo(task);
        }
    }

    static Directory cftlibBuildDir(Project project) {
        return project.getLayout().getBuildDirectory().dir("cftlib").get();
    }


    /**
     * Register the repositories that host the libraries used by the cftlib.
     */
    private void registerDependencyRepositories(Project project) {
        // We do this after evaluation to ensure these repositories are registered after those in the build script.
        project.afterEvaluate(p -> {
            String azureUrl = "https://pkgs.dev.azure.com/hmcts/Artifacts/_packaging/hmcts-lib/maven/v1";
            boolean azureRepoExists = project.getRepositories().stream()
                    .anyMatch(repo -> repo instanceof MavenArtifactRepository
                            && ((MavenArtifactRepository) repo).getUrl().toString().equals(azureUrl));

            if (!azureRepoExists) {
                p.getRepositories().maven(m -> {
                    m.setUrl(azureUrl);
                    m.setName("HMCTS Azure artifacts repository added by the Cftlib Gradle plugin");
                    m.mavenContent(MavenRepositoryContentDescriptor::releasesOnly);
                });
            }
        });
    }

    private void createCftlibJarTask(Project project) {
        var jar = project.getTasks().create("cftlibJar", Jar.class);
        jar.getArchiveFileName().set("cftlib-application.jar");
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        jar.from(s.getByName("main").getOutput().plus(s.getByName("cftlib").getOutput()));
    }

    private Configuration detachedConfiguration(Project project, Dependency... deps) {
        var result = project.getConfigurations().detachedConfiguration(deps);
        // We don't want Gradle to swap in dependency substitutions in composite builds.
        result.getResolutionStrategy().getUseGlobalDependencySubstitutionRules().set(false);
        return result;
    }

    /**
     * Ensure the source/bytecode of the cft services is picked up by the IDE,
     * since we resolve these dependencies in detached configurations that
     * the IDE would not otherwise find.
     *
     * <p>We do this by creating an otherwise unused 'cftlibIDE' sourceset and associated
     * dependency configuration.
     */
    private void surfaceSourcesToIDE(Project project) {
        project.getExtensions().getByType(SourceSetContainer.class)
                .create("cftlibIDE");
        var config = project.getConfigurations().getByName("cftlibIDEImplementation");
        var deps = projects.keySet().stream().map(Service::id).toArray(String[]::new);
        config.getDependencies().addAll(Arrays.asList(
                libDependencies(project, deps)
        ));
    }

    private void createConfigurations(Project project) {
        project.getConfigurations().getByName("cftlibImplementation")
                .extendsFrom(project.getConfigurations().getByName("implementation"))
                .getDependencies().addAll(List.of(libDependencies(project, "bootstrapper", "cftlib-agent")));

        project.getConfigurations().getByName("cftlibRuntimeOnly")
                .extendsFrom(project.getConfigurations().getByName("runtimeOnly"));
        project.getConfigurations().getByName("cftlibTestImplementation")
                .extendsFrom(project.getConfigurations().getByName("cftlibImplementation"))
                .getDependencies().addAll(List.of(
                        project.getDependencies().create("com.github.hmcts.rse-cft-lib:test-runner:"
                                + getLibVersion(project))
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


    private CftlibExec createBootWithCCDTask(Project project, ManifestTask hostAppManifestTask, String name) {
        var exec = createRunTask(project, name);
        exec.dependsOn(hostAppManifestTask);

        exec.dependsOn("cftlibClasses");

        exec.args(getHostApplicationManifestFile(project));
        return exec;
    }


    private File getHostApplicationManifestFile(Project project) {
        return cftlibBuildDir(project).file("hostApplication").getAsFile();
    }

    private ManifestTask createHostAppManifestTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlib");

        var hostApplicationManifest = getHostApplicationManifestFile(project);
        var manifestTask = project.getTasks().create("createManifestApplication", ManifestTask.class);
        manifestTask.doFirst(m -> {
            JavaExec e = (JavaExec) project.getTasks().getByName("bootRun");
            String clazz = "";

            if (e.getMainClass().isPresent()) {
                clazz = e.getMainClass().get();
            } else {
                clazz = project.getProperties().get("mainClassName").toString();
            }

            var args = "--rse.lib.service_name=" + project.getName();
            // Prepend the runtimeclasspath with the project's resources folder to allow live editing of resources
            var files = project.files("src/main/resources").plus(lib.getRuntimeClasspath());
            writeManifests(project, files, clazz, hostApplicationManifest, args);
        });
        manifestTask.classpath = lib.getRuntimeClasspath();
        // Task performing main class name resolution changed in spring boot 3
        for (String name : List.of("resolveMainClassName", "bootRunMainClassName")) {
            var t = project.getTasks().findByName(name);
            if (null != t) {
                manifestTask.dependsOn(t);
            }
        }

        manifestTasks.add(manifestTask);
        return manifestTask;
    }

    private CftlibExec createTestTask(Project project) {
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        var lib = s.getByName("cftlibTest");

        var exec = createRunTask(project, "cftlibTest");
        var file = cftlibBuildDir(project).file("libTest").getAsFile();
        var app = createManifestTask(project, "manifestTest", lib.getRuntimeClasspath(),
                "org.junit.platform.console.ConsoleLauncher", file, "--select-package=uk.gov.hmcts");

        exec.dependsOn(app);
        exec.dependsOn("cftlibClasses");
        exec.dependsOn("cftlibTestClasses");
        exec.args(file);
        exec.environment("RSE_LIB_STUB_AUTH_OUTBOUND", "true");
        exec.getOutputs().upToDateWhen(x -> false);
        return exec;
    }

    private void createManifestTasks(Project project) {
        {
            // Runtime is always the first manifest
            var file = cftlibBuildDir(project).file("runtime").getAsFile();
            Configuration classpath = detachedConfiguration(project,
                libDependencies(project, "runtime"));
            var task =
                createManifestTask(project, "writeManifestRuntime", classpath,
                    "uk.gov.hmcts.rse.ccd.lib.Application", file);
            manifestTasks.add(task);
            manifests.add(file);
        }

        for (var e : projects.entrySet()) {
            var service = e.getKey();
            var file = cftlibBuildDir(project).file(service.id()).getAsFile();
            var args = Lists.newArrayList(
                "--rse.lib.service_name=" + service);

            args.addAll(service.args);
            manifestTasks.add(
                createServiceManifestTask(project, service, e.getValue(), file, args.toArray(String[]::new)));
            manifests.add(file);
        }
    }

    private ManifestTask createManifestTask(Project project, String name, FileCollection configuration,
                                            String mainClass, File file, String... args) {
        var result = project.getTasks().create(name, ManifestTask.class);
        result.classpath = configuration;
        result.doFirst(x -> {
            writeManifests(project, configuration, mainClass, file, args);
        });
        result.getOutputs().file(file);
        return result;
    }

    @SneakyThrows
    private void writeManifests(Project project, FileCollection classpath, String mainClass, File file,
                                String... args) {
        cftlibBuildDir(project).getAsFile().mkdirs();
        writeManifest(file, mainClass, classpath, File::getAbsolutePath, args);
    }

    @SneakyThrows
    private void writeManifest(File file, String mainClass, FileCollection classpath,
                               Function<File, String> pathResolver, String... args) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(mainClass + " " + Joiner.on(" ").join(args));
            for (var f : classpath) {
                writer.println(pathResolver.apply(f));
            }
        }
    }

    private String getLibVersion(Project project) {
        return project.getBuildscript().getConfigurations()
            .getByName("classpath")
            .getDependencies()
            .stream()
            .filter(x -> x.getGroup().equals("com.github.hmcts.rse-cft-lib")
                && x.getName().equals("com.github.hmcts.rse-cft-lib.gradle.plugin"))
            .findFirst()
            .map(Dependency::getVersion)
            .orElse("DEV-SNAPSHOT");
    }

    private CftlibExec createRunTask(Project project, String name) {
        CftlibExec j = project.getTasks().create(name, CftlibExec.class);
        j.getMainClass().set("uk.gov.hmcts.rse.ccd.lib.LibRunner");
        // TODO: Wire inputs and outputs
        j.getOutputs().upToDateWhen(x -> false);


        j.doFirst(x -> {
            // Resolve the configuration as a detached configuration for isolation from
            // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
            j.classpath(detachedConfiguration(project, libDependencies(project, "bootstrapper")));
        });

        j.args(manifests);
        j.dependsOn(manifestTasks);
        return j;
    }


    Dependency[] libDependencies(Project project, String... libDeps) {
        return Arrays.stream(libDeps)
                .map(d -> project.getDependencies()
                        .create("com.github.hmcts.rse-cft-lib:" + d + ":" + getLibVersion(project)))
                .toArray(Dependency[]::new);
    }

    private ManifestTask createServiceManifestTask(Project project, Service service, String mainClass, File file,
                                                   String... args) {
        var result = project.getTasks().create("writeManifest" + service.id(), ManifestTask.class);
        result.doFirst(x -> {
            var dependency = resolveDependencyFor(project, service);
            Configuration classpath = detachedConfiguration(project,
                    libDependencies(project, dependency, "cftlib-agent"));
            result.classpath = classpath;
            writeManifests(project, classpath, mainClass, file, args);
        });
        result.getOutputs().file(file);
        return result;
    }

    private String resolveDependencyFor(Project project, Service service) {
        if (service == Service.ccdDataStoreApi) {
            var extraProperties = project.getExtensions().getExtraProperties();
            if (extraProperties.has("cftlib.datastore")) {
                var value = extraProperties.get("cftlib.datastore").toString().trim();
                if ("decentralised".equalsIgnoreCase(value) || "decentralized".equalsIgnoreCase(value)) {
                    return "ccd-data-store-api-decentralised";
                }
            }
        }
        return service.id();
    }
}
