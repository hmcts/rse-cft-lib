package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    if (System.getenv("CFT_LIB_NO_DOCKER") == null) {
      new Thread(new ComposeRunner()::startBoot).start();
    } else {
      ControlPlane.setDBReady();
      ControlPlane.setESReady();
      ControlPlane.setAuthReady();
    }

    SpringApplication.run(Application.class, args);
  }
}
