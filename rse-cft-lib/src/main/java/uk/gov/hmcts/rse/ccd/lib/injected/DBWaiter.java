package uk.gov.hmcts.rse.ccd.lib.injected;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.impl.ComposeRunner;

@Aspect
@Component
public class DBWaiter {

  // Block any database access until ready for use.
  @Before("execution(* javax.sql.DataSource.*(..))")
  public void waitForDB() {
    ComposeRunner.waitForDB();
  }

  // Block any use of ElasticSearch until ready for use.
  @Before("execution(* uk.gov.hmcts.ccd.definition.store.elastic.client.*.*(..))")
  public void waitForES() {
    ComposeRunner.waitForES();
  }
}
