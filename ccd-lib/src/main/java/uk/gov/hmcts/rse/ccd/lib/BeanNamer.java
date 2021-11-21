package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

public class BeanNamer implements BeanNameGenerator {
  @Override
  public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
    // Package qualified to avoid conflicting class names.
    return definition.getBeanClassName();
  }
}
