package uk.gov.hmcts.rse.ccd.lib;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import lombok.RequiredArgsConstructor;

@Retention(RetentionPolicy.RUNTIME)
@interface ForProjects {
  DBProxy.project[] value();
}
