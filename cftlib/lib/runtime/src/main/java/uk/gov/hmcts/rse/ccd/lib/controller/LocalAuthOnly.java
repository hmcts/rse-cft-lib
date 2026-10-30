package uk.gov.hmcts.rse.ccd.lib.controller;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import uk.gov.hmcts.rse.ccd.lib.CftlibConfig;

/**
 * Registers a stand-in only with local auth. With AAT auth the services use AAT's own, and a stand-in that forwards
 * to the IDAM simulator would answer for something that is not running.
 */
public class LocalAuthOnly implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return CftlibConfig.localAuth();
    }
}
