package uk.gov.hmcts.rse.ccd.lib.controller;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import uk.gov.hmcts.rse.ccd.lib.CftlibConfig;
import uk.gov.hmcts.rse.ccd.lib.Features;

/**
 * The reference data stand-in runs with the refData feature, which needs local auth.
 */
public class RefDataOnly implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return CftlibConfig.localAuth() && Features.enabled("refData");
    }
}
