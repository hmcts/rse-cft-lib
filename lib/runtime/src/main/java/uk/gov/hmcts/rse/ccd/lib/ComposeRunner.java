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
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import org.awaitility.Awaitility;
import org.zeroturnaround.exec.ProcessExecutor;
import uk.gov.hmcts.rse.ccd.lib.impl.Project;

public class ComposeRunner {

    public static void main(String[] args) {
        new ComposeRunner().startBoot();
    }

    void startBoot() {
      try {
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

      new ProcessExecutor().command("docker-compose", "-p", "cftlib", "up", "--build", "-d")
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

    @SneakyThrows
    boolean dbReady() {
      try (var c = DriverManager.getConnection(
          "jdbc:postgresql://localhost:6432/postgres",
          "postgres", "postgres")) {

        // Create the databases if necessary.
        for (var db : List.of(Project.Datastore, Project.Definitionstore, Project.Userprofile, Project.AM)) {
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
