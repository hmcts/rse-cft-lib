package uk.gov.hmcts.rse.ccd.lib;

import javax.inject.Named;
import lombok.SneakyThrows;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

public class BeanNamer implements BeanNameGenerator {
  @SneakyThrows
  @Override
  public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
    // Don't override (and break) explicit names.
    Named n = Class.forName(definition.getBeanClassName())
        .getAnnotation(Named.class);
    if (null != n && !"".equals(n.value())) {
      String r = n.value();
      return r;
    }
    // Use a package qualified name to avoid the same class name causing conflicts.
    return definition.getBeanClassName();
  }
}
