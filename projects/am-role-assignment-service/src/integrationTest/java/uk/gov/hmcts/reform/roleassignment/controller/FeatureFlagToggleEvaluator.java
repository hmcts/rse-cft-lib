package uk.gov.hmcts.reform.roleassignment.controller;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import uk.gov.hmcts.reform.roleassignment.annotations.FeatureFlagToggle;

public class FeatureFlagToggleEvaluator implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return new IgnorableStatement(base, description);
    }

    private static class IgnorableStatement extends Statement {

        private final Statement base;
        private final Description description;

        public IgnorableStatement(Statement base, Description description) {
            this.base = base;
            this.description = description;
        }

        @Override
        public void evaluate() throws Throwable {
            FeatureFlagToggle featureFlagToggle = description.getAnnotation(FeatureFlagToggle.class);
            if (featureFlagToggle != null) {
                Assume.assumeTrue("Test is ignored!", featureFlagToggle.flagEnabled());
                base.evaluate();
            }
        }
    }
}
