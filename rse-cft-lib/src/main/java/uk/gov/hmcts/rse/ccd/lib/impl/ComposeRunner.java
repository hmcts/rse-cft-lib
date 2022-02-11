package uk.gov.hmcts.rse.ccd.lib.impl;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.stereotype.Component;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

@Slf4j
@Component
public class ComposeRunner {
  private static volatile Throwable INIT_EXCEPTION;
  private static final CountDownLatch DB_READY = new CountDownLatch(1);
  private static final CountDownLatch ES_READY = new CountDownLatch(1);

  @SneakyThrows
  public static void waitForDB() {
    DB_READY.await();
    if (INIT_EXCEPTION != null) {
      throw INIT_EXCEPTION;
    }
  }

  @SneakyThrows
  public static void waitForES() {
    ES_READY.await();
    if (INIT_EXCEPTION != null) {
      throw INIT_EXCEPTION;
    }
  }

  private static volatile boolean booted;
  public static class RunListener implements SpringApplicationRunListener {
    public RunListener(SpringApplication app, String[] args) {
      // Constructors are synchronized in Java,
      // so this is thread-safe.
      if (!booted) {
        booted = true;
        new Thread(this::startBoot).start();
      }
    }

    void startBoot() {
      try {
        dockerBoot();
      } catch (Exception e) {
        INIT_EXCEPTION = e;
        DB_READY.countDown();
        ES_READY.countDown();
      }
    }

    @SneakyThrows
    void dockerBoot() {
      var f = File.createTempFile("cftlib", "");
      URL u = getClass().getResource("/cftlib-compose.zip");
      FileUtils.copyURLToFile(u, f);
      var dir = Files.createTempDirectory("cftlib");
      new ZipFile(f).extractAll(dir.toString());

      new ProcessExecutor().command("docker-compose", "-p", "cftlib", "up", "--build", "-d")
          .redirectOutput(Slf4jStream.of(log).asInfo())
          .redirectError(Slf4jStream.of(log).asError())
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

      DB_READY.countDown();

      // Wait for elasticsearch
      Awaitility.await()
          .pollInSameThread()
          .pollInterval(Duration.ofMillis(250))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .timeout(10, TimeUnit.MINUTES)
          .until(this::esReady);

      ES_READY.countDown();
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
        log.info("DB not yet available...");
        throw s;
      }
      return true;
    }

    @SneakyThrows
    private boolean esReady() {
      Thread.currentThread().setName("****SDFSDFDFDFDF");
      try {
        var c = (HttpURLConnection) new URL("http://localhost:9200/_cat/health")
            .openConnection();
        if (c.getResponseCode() != 200) {
          return false;
        }
      } catch (Exception e){
        log.info("ES not yet available...");
        throw e;
      }
      return true;
    }
  }
}
