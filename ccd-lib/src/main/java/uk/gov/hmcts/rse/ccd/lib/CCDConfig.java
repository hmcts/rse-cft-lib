package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;

@Configuration
@ComponentScan(value = {
    "uk.gov.hmcts.rse.ccd.lib",
    "uk.gov.hmcts.ccd.definition"
}, excludeFilters = {
    // Def/Data transaction managers are identical
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*TransactionConfiguration\\.*"),
    // Disable the default application component scanning or our excludes won't work.
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {CaseDataAPIApplication.class})
})
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.ccd.definition")
@EntityScan(basePackages = "uk.gov.hmcts.ccd.definition")
public class CCDConfig {

  public CCDConfig() {

  }

}
