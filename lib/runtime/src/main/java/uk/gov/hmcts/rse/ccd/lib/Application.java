package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    if (System.getenv("CFT_LIB_NO_DOCKER") == null) {
      new Thread(new ComposeRunner()::startBoot).start();
    } else {
      ControlPlane.setApi(new CFTLibApiImpl());
      ControlPlane.setDBReady();
      ControlPlane.setAuthReady();
      ControlPlane.setESReady();
    }

    SpringApplication.run(Application.class, args);
  }
}
