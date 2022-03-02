package uk.gov.hmcts.rse.ccd.lib;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Aspect
@Configuration
// Scan the rest of the package to pick up the other classes.
@ComponentScan
public class LibAgent {

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

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
      ControlPlane.appReady();
  }
}
