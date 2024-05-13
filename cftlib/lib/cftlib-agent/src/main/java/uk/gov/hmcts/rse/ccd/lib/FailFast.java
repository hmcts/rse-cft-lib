package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

// Fast shutdown the JVM (ie. all services) if any
// individual service fails to boot.
public class FailFast extends AbstractFailureAnalyzer {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
        ControlPlane.failFast.uncaughtException(Thread.currentThread(), rootFailure);
        // Unreachable
        throw new RuntimeException();
    }
}
