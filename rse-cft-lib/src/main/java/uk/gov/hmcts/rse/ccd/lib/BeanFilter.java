//package uk.gov.hmcts.rse.ccd.lib;
//
//import java.util.Set;
//import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
//import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
//import org.springframework.beans.factory.support.BeanDefinitionRegistry;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//class BeanFilter {
//
//  private static Set<String> exclude = Set.of(
//      "serviceAuthHealthIndicator",
//      // Disable the CCD client healthcheck (to avoid infinite recursion)
//      // https://github.com/hmcts/ccd-client/blob/954e7a593d3854577df1606b54bd08dbaba5d5cf/src/main/java/uk/gov/hmcts/reform/ccd/client/CoreCaseDataClientAutoConfiguration.java#L15
//      "coreCaseData",
//
//      // AM health checks
//      "uk.gov.hmcts.reform.roleassignment.health.IdamServiceHealthIndicator",
//      "uk.gov.hmcts.reform.roleassignment.health.CcdDataStoreHealthIndicator"
//  );
//
//  @Bean
//  public static BeanFactoryPostProcessor registerPostProcessor() {
//    return (ConfigurableListableBeanFactory beanFactory) -> {
//      BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
//      for (String s : exclude) {
//        if (registry.containsBeanDefinition(s)) {
//          registry.removeBeanDefinition(s);
//        }
//      }
//    };
//  }
//}
//
