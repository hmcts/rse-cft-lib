package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import org.awaitility.Awaitility;
import org.zeroturnaround.exec.ProcessExecutor;

public class ComposeRunner {

    void startBoot() {
      try {
        ControlPlane.setApi(new CFTLibApiImpl());
        dockerBoot();
      } catch (Exception e) {
          ControlPlane.setDBError(e);
          ControlPlane.setESError(e);
      }
    }

    @SneakyThrows
    void dockerBoot() {
      var f = File.createTempFile("cftlib", "");
      URL u = getClass().getResource("/cftlib-compose.zip");
      try (InputStream i = u.openStream()) {
          Files.copy(i, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      var dir = Files.createTempDirectory("cftlib");
      new ZipFile(f).extractAll(dir.toString());
      var args = new ArrayList<String>(List.of("docker-compose", "-p", "cftlib", "up", "--build", "-d"));
      // When running on a CI server ensure clean container builds.
      if (null != System.getenv("CI")) {
        args.add("--force-recreate");
      }

      new ProcessExecutor().command(args)
        .environment(getEnvironmentVars())
          .redirectOutput(System.out)
          .redirectError(System.err)
          .directory(dir.toFile())
          .exitValueNormal()
          .timeout(10, TimeUnit.MINUTES)
          .execute();

      Awaitility.await()
        .pollInSameThread()
        .pollInterval(Duration.ofMillis(100))
        .pollDelay(Duration.ZERO)
        .ignoreExceptions()
        .timeout(10, TimeUnit.MINUTES)
        .until(this::authReady);

      ControlPlane.setAuthReady();

      Awaitility.await()
          .pollInSameThread()
          .pollInterval(Duration.ofMillis(100))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .timeout(10, TimeUnit.MINUTES)
          .until(this::dbReady);

      ControlPlane.setDBReady();

      // Wait for elasticsearch
      Awaitility.await()
          .pollInSameThread()
          .pollInterval(Duration.ofMillis(250))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .timeout(10, TimeUnit.MINUTES)
          .until(this::esReady);

      ControlPlane.setESReady();
    }

  private Map<String, String> getEnvironmentVars() {
      if ("localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
        var hostEnv = System.getenv("JVM_HOST");
        var host = "http://" + (
          hostEnv != null
          ? hostEnv
          : "host.docker.internal");
        var runtime = host + ":5556";
        return Map.of(
        "XUI_S2S_URL", host + ":8489",
        "XUI_IDAM_API_URL", runtime,
        "XUI_IDAM_LOGIN_URL", "http://localhost:5556",
        // TODO: placeholder to pass health checks
        "XUI_EM_DOCASSEMBLY_API", runtime,
        "XUI_DOCUMENTS_API", runtime,
        "XUI_DOCUMENTS_API_V2", runtime
        );
      }
    return Map.of();
  }

  @SneakyThrows
  private boolean authReady() {
    if ("localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
      try {
        // Wait for Idam Simulator to come up.
        var c = (HttpURLConnection) new URL("http://localhost:5556/health")
          .openConnection();
        if (c.getResponseCode() != 200) {
          throw new RuntimeException();
        }
      } catch (Exception e) {
        System.out.println("Idam not ready...");
        return false;
      }
    }
    return true;
  }

  @SneakyThrows
    boolean dbReady() {
      try (var c = DriverManager.getConnection(
          "jdbc:postgresql://localhost:6432/postgres",
          "postgres", "postgres")) {

        var dbs = List.of(Project.Datastore, Project.Definitionstore, Project.Userprofile, Project.AM)
          .stream().map(Objects::toString)
          .collect(Collectors.toCollection(ArrayList::new));

        var additionalDbs = System.getenv("RSE_LIB_ADDITIONAL_DATABASES");
        if (additionalDbs != null) {
          dbs.addAll(List.of(additionalDbs.split(",")));
        }

        // Create the databases if necessary.
        for (var db : dbs) {
          var s = c.prepareStatement(String.format("SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower('%s')", db));
          s.execute();
          if (!s.getResultSet().next()) {
            c.prepareStatement("create database " + db).execute();
          }
        }
      } catch (SQLException s) {
        System.out.println("DB not yet available...");
        throw s;
      }
      return true;
    }

    @SneakyThrows
    private boolean esReady() {
      try {
        var c = (HttpURLConnection) new URL("http://localhost:9200/_cat/health")
            .openConnection();
        if (c.getResponseCode() != 200) {
          return false;
        }
      } catch (Exception e){
        System.out.println("ES not yet available...");
        throw e;
      }
      return true;
    }
}
