package uk.gov.hmcts.rse.ccd.lib.controller;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import uk.gov.hmcts.rse.ccd.lib.CftlibConfig;
import uk.gov.hmcts.rse.ccd.lib.Features;

/**
 * Local-auth consumers keep their existing document stores and IDAM routes unless they request localServices.
 */
public class LocalServicesOnly implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return CftlibConfig.localAuth() && Features.enabled("localServices");
    }
}
