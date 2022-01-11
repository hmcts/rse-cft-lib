package uk.gov.hmcts.rse.ccd.lib;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface ForProjects {
  DBProxy.project[] value();
}
