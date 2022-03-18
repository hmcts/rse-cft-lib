package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    new Thread(new ComposeRunner()::startBoot).start();
    SpringApplication.run(Application.class, args);
  }
}
