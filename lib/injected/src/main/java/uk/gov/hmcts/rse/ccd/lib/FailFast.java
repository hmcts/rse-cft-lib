package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

// Fast shutdown the JVM (ie. all services) if any
// individual service fails to boot.
public class FailFast extends AbstractFailureAnalyzer {
  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
    cause.printStackTrace();
    System.out.println("**** CFTLIB failed to start ****");
    Runtime.getRuntime().halt(-1);
    // Unreachable
    throw new RuntimeException();
  }
}
