package uk.gov.hmcts.rse.ccd.lib;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@Configuration
//@PropertySource({"classpath:persistence-multiple-db-boot.properties"})
@EnableJpaRepositories(
    basePackages = "uk.gov.hmcts.ccd",
    entityManagerFactoryRef = "entityManagerFactory"
)
public class DataStoreJPAConfig {

  @Bean
  @Primary
  @ConfigurationProperties(prefix="spring.datasource")
  public DataSource dataStoreDataSource() {
    return DataSourceBuilder.create().build();
  }

  @Bean()
  @Primary
  public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
    LocalContainerEntityManagerFactoryBean em
        = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataStoreDataSource());
    em.setPackagesToScan("uk.gov.hmcts.ccd");

    HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    em.setJpaVendorAdapter(vendorAdapter);

    return em;
  }

}
