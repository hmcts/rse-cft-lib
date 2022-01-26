package uk.gov.hmcts.rse.ccd.lib.v2.lib;

import org.springframework.context.annotation.Configuration;

//@ComponentScan(basePackages = "uk.gov.hmcts.rse.ccd.lib.v2")
//@AutoConfigureBefore({
//     Register our JPA config before the spring Hibernate auto config
//     so our beans (EntityManager) stop spring creating its own.
//    HibernateJpaAutoConfiguration.class
//})
//@PropertySource(value = {
//    "classpath:definitionstore/application.properties"
//})
//@PropertySource(value = {
//}
//    , factory = YamlPropertySourceFactory.class)
//@EnableAspectJAutoProxy
@Configuration
public class ParentContextConfiguration {

  // Because we disable CoreCaseDataApplication.class from scanning
//  @ConditionalOnMissingBean
//  @Bean
//  public Clock utcClock() {
//    return Clock.systemUTC();
//  }

}
