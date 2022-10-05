package uk.gov.hmcts.rse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import lombok.SneakyThrows;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.jvm.tasks.Jar;

public class CftLibPlugin implements Plugin<Project> {

    private final List<File> manifests = new ArrayList<>();
    private final List<ManifestTask> manifestTasks = Lists.newArrayList();

    public void apply(Project project) {
        project.getPlugins().apply("java");

        project.afterEvaluate(p -> {
            // CCD repos still rely on jcenter so add it as last repository.
            p.getRepositories().jcenter();
        });

        createSourceSets(project);
        createConfigurations(project);

        registerDependencyRepositories(project);
        createManifestTasks(project);
        createBootWithCCDTask(project);
        createTestTask(project);
        surfaceSourcesToIDE(project);
        createCftlibJarTask(project);
        createExecutableJarTask(project, createZipRuntimeTask(project));
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
            p.getRepositories().mavenCentral();
            p.getRepositories().maven(m -> m.setUrl("https://jitpack.io"));
        });
    }

    @SneakyThrows
    private Zip createZipRuntimeTask(Project project) {
        var zip = project.getTasks().create("cftlibRuntimeArchive", Zip.class);
        zip.getArchiveFileName().set("cftlib-runtime.zip");
        // Jars are already compressed so switch off compression.
        zip.setEntryCompression(ZipEntryCompression.STORED);
        zip.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        zip.from(cftlibBuildDir(project), z -> {
            z.include("**/*_packed");
            z.exclude("**/*libTest*");
        });

        zip.eachFile(f -> {
            f.setRelativePath(zipPath(project, f.getFile()));
        });

        for (ManifestTask manifestTask : manifestTasks) {
            zip.from(manifestTask.classpath, x -> x.into("lib"));
            zip.dependsOn(manifestTask);
        }
        return zip;
    }

    /**
     * Calculate the correct path that files should be placed inside the zip file.
     * We pack files from both the project folder (into 'build') and Gradle's dependency cache (into 'lib').
     */
    @SneakyThrows
    private RelativePath zipPath(Project project, File f) {
        var projectRoot = project.getLayout().getBuildDirectory().get().dir("../").getAsFile().getCanonicalPath();
        if (f.getCanonicalPath().startsWith(projectRoot)) {
            // File should go into 'build'
            var relativePath = Paths.get(projectRoot).relativize(f.toPath());
            return RelativePath.parse(true, relativePath.toString());
        } else {
            // File should go into 'lib'
            var p = RelativePath.parse(true, f.getPath());
            // The last 4 segments of the jar's directory path include its group, name and version.
            var tail = Arrays.copyOfRange(p.getSegments(), p.getSegments().length - 4, p.getSegments().length);
            var result = ObjectArrays.concat("lib", tail);
            return new RelativePath(true, result);
        }
    }

    private void createCftlibJarTask(Project project) {
        var jar = project.getTasks().create("cftlibJar", Jar.class);
        jar.getArchiveFileName().set("cftlib-application.jar");
        SourceSetContainer s = project.getExtensions().getByType(SourceSetContainer.class);
        jar.from(s.getByName("main").getOutput().plus(s.getByName("cftlib").getOutput()));
    }

    private void createExecutableJarTask(Project project, Zip archive) {

        var jar = project.getTasks().create("cftlibExecutableJar", Jar.class);
        jar.dependsOn(manifestTasks);
        jar.manifest(x -> x.attributes(Map.of("Main-Class", Library.Bootstrapper.mainClass)));
        jar.getArchiveFileName().set("cftlib-executable.jar");

        jar.setEntryCompression(ZipEntryCompression.STORED);
        jar.doFirst(t -> {
            jar.from(project.zipTree(
                project.getConfigurations().detachedConfiguration(libDependencies(project, Library.Bootstrapper))
                    .getSingleFile()));
        });
        jar.from(archive);
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
        config.getDependencies().addAll(Arrays.asList(
            libDependencies(project, Library.values())
        ));
    }

    private void createConfigurations(Project project) {
        project.getConfigurations().getByName("cftlibImplementation")
            .extendsFrom(project.getConfigurations().getByName("implementation"))
            .getDependencies().addAll(List.of(libDependencies(project, Library.Bootstrapper, Library.CftlibAgent)));

        project.getConfigurations().getByName("cftlibRuntimeOnly")
            .extendsFrom(project.getConfigurations().getByName("runtimeOnly"));
        project.getConfigurations().getByName("cftlibTestImplementation")
            .extendsFrom(project.getConfigurations().getByName("cftlibImplementation"))
            .getDependencies().addAll(List.of(
                project.getDependencies().create(Library.TestRunner.mavenCoordinates(getLibVersion(project)))
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

        var file = cftlibBuildDir(project).file("application").getAsFile();
        var manifest = project.getTasks().create("createManifestApplication", ManifestTask.class);
        manifest.doFirst(m -> {
            JavaExec e = (JavaExec) project.getTasks().getByName("bootRun");
            String clazz = "";

            if (e.getMainClass().isPresent()) {
                clazz = e.getMainClass().get();
            } else {
                clazz = project.getProperties().get("mainClassName").toString();
            }

            var args = "--rse.lib.service_name=" + project.getName();
            writeManifests(project, lib.getRuntimeClasspath(), clazz, file, args);
        });
        manifest.classpath = lib.getRuntimeClasspath();
        if (null != project.getTasks().findByName("bootRunMainClassName")) {
            manifest.dependsOn(project.getTasks().getByName("bootRunMainClassName"));
        }
        manifestTasks.add(manifest);
        var exec = createRunTask(project, "bootWithCCD");
        exec.dependsOn(manifest);

        exec.dependsOn("cftlibClasses");

        exec.args(file);
    }

    private void createTestTask(Project project) {
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
    }

    private void createManifestTasks(Project project) {
        {
            // Runtime is always the first manifest
            var file = cftlibBuildDir(project).file("runtime").getAsFile();
            Configuration classpath = project.getConfigurations().detachedConfiguration(
                libDependencies(project, Library.Runtime));
            var task =
                createManifestTask(project, "writeManifestRuntime", classpath, Library.Runtime.mainClass,
                    file);
            manifestTasks.add(task);
            manifests.add(file);
        }

        for (var p : Library.values()) {
            if (p.mainClass == null || p.equals(Library.Runtime)) {
                continue;
            }
            var file = cftlibBuildDir(project).file(p.toString()).getAsFile();
            var args = Lists.newArrayList(
                "--rse.lib.service_name=" + p.name
            );
            if (p.equals(Library.CCDDataStore)) {
                args.add("--idam.client.secret=${IDAM_OAUTH2_DATA_STORE_CLIENT_SECRET:}");
            } else if (p.equals(Library.AACManageCaseAssignment)) {
                args.add("--idam.client.secret=${IDAM_OAUTH2_AAC_CLIENT_SECRET:}");
            }
            manifestTasks.add(
                createCFTManifestTask(project, p, file, args.toArray(String[]::new)));
            manifests.add(file);
        }
    }

    private ManifestTask createCFTManifestTask(Project project, Library lib, File file,
                                               String... args) {
        Configuration classpath = project.getConfigurations().detachedConfiguration(
            libDependencies(project, lib, Library.CftlibAgent));
        return createManifestTask(project, "writeManifest" + lib, classpath, lib.mainClass, file, args);
    }

    private ManifestTask createManifestTask(Project project, String name, FileCollection configuration,
                                            String mainClass, File file, String... args) {
        var result = project.getTasks().create(name, ManifestTask.class);
        result.classpath = configuration;
        result.doFirst(x -> {
            writeManifests(project, configuration, mainClass, file, args);
        });
        return result;
    }

    @SneakyThrows
    private void writeManifests(Project project, FileCollection classpath, String mainClass, File file,
                                String... args) {
        cftlibBuildDir(project).getAsFile().mkdirs();
        writeManifest(file, mainClass, classpath, File::getAbsolutePath, args);
        writeManifest(new File(file.getPath() + "_packed"), mainClass, classpath,
            x -> zipPath(project, x).getPathString(), args);
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
        j.setMain(Library.Bootstrapper.mainClass);


        j.doFirst(x -> {
            // Resolve the configuration as a detached configuration for isolation from
            // the project's build (eg. to prevent interference from spring boot's dependency mgmt plugin)
            j.classpath(project.getConfigurations().detachedConfiguration(libDependencies(project,
                    Library.Bootstrapper)));
        });

        j.args(manifests);
        j.dependsOn(manifestTasks);
        return j;
    }


    Dependency[] libDependencies(Project project, Library... libDeps) {
        return Arrays.stream(libDeps)
            .map(d -> project.getDependencies()
                .create(d.mavenCoordinates(getLibVersion(project))))
            .toArray(Dependency[]::new);
    }
}
