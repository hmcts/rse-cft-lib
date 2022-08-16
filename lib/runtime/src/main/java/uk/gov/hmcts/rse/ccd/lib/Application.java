package uk.gov.hmcts.rse.ccd.lib;

import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import uk.gov.hmcts.rse.ccd.lib.config.DefinitionConfig;

@SpringBootApplication(
    scanBasePackages = {"uk.gov.hmcts.rse.ccd.lib"}
)
@EnableConfigurationProperties(DefinitionConfig.class)
public class Application {
    @SneakyThrows
    public static void main(String[] args) {
        // Load and register the postgres driver in java.sql.DriverManager
        Class.forName("org.postgresql.Driver");
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
