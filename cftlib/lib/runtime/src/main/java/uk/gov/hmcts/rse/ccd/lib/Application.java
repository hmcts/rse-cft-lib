package uk.gov.hmcts.rse.ccd.lib;

import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    @SneakyThrows
    public static void main(String[] args) {
        // Load and register the postgres driver in java.sql.DriverManager
        Class.forName("org.postgresql.Driver");
        new Thread(new ComposeRunner()::startBoot).start();
        SpringApplication.run(Application.class, args);
    }
}
