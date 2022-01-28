package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.net.URL;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

@Slf4j
@Component
public class ComposeRunner {
  public static CountDownLatch DB_READY = new CountDownLatch(1);

  @SneakyThrows
  @EventListener(ApplicationReadyEvent.class)
  public void configure() {
    System.out.println();
  }

  private static boolean booted;
  public static class RunListener implements SpringApplicationRunListener {
    public RunListener(SpringApplication app, String[] args) {
      if (!booted) {
        booted = true;
        new Thread(this::dockerBoot).start();
      }
    }


    @Override
    public void running(ConfigurableApplicationContext context) {
      SpringApplicationRunListener.super.running(context);
    }

    @SneakyThrows
    void dockerBoot() {
      var f = File.createTempFile("cftlib", "");
      URL u = getClass().getResource("/rse/cftlib-docker-compose.yml");
      FileUtils.copyURLToFile(u, f);

      var environment = Map.of("COMPOSE_FILE", f.getName());
      new ProcessExecutor().command("docker-compose", "up", "-d")
          .redirectOutput(Slf4jStream.of(log).asInfo())
          .redirectError(Slf4jStream.of(log).asInfo())
          .directory(f.getParentFile())
          .environment(environment)
          .exitValueNormal()
          .timeout(10, TimeUnit.MINUTES)
          .execute();

      Callable<Boolean> ready = () -> {
        try (var c = DriverManager.getConnection(
            "jdbc:postgresql://localhost:6432/postgres",
            "postgres", "postgres")) {

          // Create the databases if necessary.
          for (var db : List.of("datastore", "definitionstore", "am", "userprofile")) {
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
      };
      Awaitility.await()
          .pollInSameThread()
          .pollInterval(Duration.ofMillis(100))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .forever()
          .until(ready);

      DB_READY.countDown();
    }
  }
}
