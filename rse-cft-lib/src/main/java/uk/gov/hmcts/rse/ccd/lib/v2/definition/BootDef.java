package uk.gov.hmcts.rse.ccd.lib.v2.definition;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.CoreCaseDataApplication;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.SecurityConfiguration;
import uk.gov.hmcts.ccd.definition.store.SwaggerConfiguration;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.rse.ccd.lib.YamlPropertySourceFactory;

public class BootDef {

  @SpringBootApplication(
      scanBasePackageClasses = CaseDataAPIApplication.class
  )
  @ComponentScan(
      basePackageClasses = CaseDataAPIApplication.class,
      excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
          CaseDataAPIApplication.class,
          SwaggerConfiguration.class,
          SecurityConfiguration.class
      }))
  @PropertySources({
      @PropertySource(value = {
          "classpath:cftlib-defstore-es.yml"
      }
          , factory = YamlPropertySourceFactory.class),
      @PropertySource("classpath:definitionstore/application.properties"),
      @PropertySource("classpath:rse/definitionstore.properties"),
      @PropertySource("classpath:rse/application.properties"),
  })
  @EntityScan(basePackages = {
      "uk.gov.hmcts.ccd.definition.store"
  })
  @EnableJpaRepositories(basePackages = {
      "uk.gov.hmcts.ccd.definition.store"
  })
//  @EnableFeignClients(
//      clients = {
//          IdamApi.class,
//      })
  public static class DefinitionStore {
  }
}
