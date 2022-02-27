package uk.gov.hmcts.rse.ccd.lib;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DBWaiter {

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
}
