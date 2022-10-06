package uk.gov.hmcts.rse.ccd.lib;

import java.util.ArrayList;
import java.util.List;

import lombok.SneakyThrows;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

// Injected into the classpath of the applications we start
// to supervise the boot process and run any CftLibConfig once complete.
@Aspect
@Configuration
// Scan the rest of the package to pick up the other classes.
@ComponentScan
public class LibAgent {

    @Autowired(required = false)
    private List<CFTLibConfigurer> configurers = new ArrayList<>();

    // Block any database access until ready for use.
    @Before("execution(* javax.sql.DataSource.*(..))")
    public void waitForDB() {
        ControlPlane.waitForDB();
    }

    // Block any use of ElasticSearch until ready for use.
    @Before("execution(* uk.gov.hmcts.ccd.definition.store.elastic.client.*.*(..))")
    public void waitForES() {
        ControlPlane.waitForES();
    }

    @SneakyThrows
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ControlPlane.appReady();
        // If this application defines any cftlib configs then execute them once fully booted up.
        for (CFTLibConfigurer configurer : configurers) {
            configurer.configure(ControlPlane.getApi());
        }
    }
}
