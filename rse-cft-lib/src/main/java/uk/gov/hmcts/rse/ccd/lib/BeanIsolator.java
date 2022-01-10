package uk.gov.hmcts.rse.ccd.lib;

import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.type.MethodMetadata;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BeanIsolator implements BeanFactoryPostProcessor {

  @Override
  public void postProcessBeanFactory(final ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    var beanFactory = (DefaultListableBeanFactory) configurableListableBeanFactory;
    beanFactory.setAutowireCandidateResolver(
        new Isolator(beanFactory.getAutowireCandidateResolver())
    );
  }

  @RequiredArgsConstructor
  public static class Isolator implements AutowireCandidateResolver {
    private final AutowireCandidateResolver resolver;

    @Override
    public boolean isAutowireCandidate(final BeanDefinitionHolder bdHolder, final DependencyDescriptor descriptor) {
      var potential = resolver.isAutowireCandidate(bdHolder, descriptor);

      if (potential && bdHolder.getSource() instanceof MethodMetadata) {
        var m = (MethodMetadata) bdHolder.getSource();
        if (m.getAnnotations().isPresent(ForProjects.class)) {
          var allowableProjects = (DBProxy.project[]) m.getAnnotations().get(ForProjects.class).getValue("value").get();
          var targetPackage = descriptor.getMember().getDeclaringClass().getPackageName();
          var targetProject = DBProxy.detectSchema(targetPackage);
          var result = !Arrays.asList(allowableProjects).contains(targetProject);
          log.info("Can bean {} can be injected into {}?: {}", bdHolder.getBeanName(), targetPackage, result);
          return result;
        }
      }
      return potential;
    }

    @Override
    public boolean isRequired(DependencyDescriptor descriptor) {
      return resolver.isRequired(descriptor);
    }

    @Override
    public boolean hasQualifier(DependencyDescriptor descriptor) {
      return resolver.hasQualifier(descriptor);
    }

    @Override
    public Object getSuggestedValue(DependencyDescriptor descriptor) {
      return resolver.getSuggestedValue(descriptor);
    }

    @Override
    public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
      return resolver.getLazyResolutionProxyIfNecessary(descriptor, beanName);
    }

    @Override
    public AutowireCandidateResolver cloneIfNecessary() {
      return resolver.cloneIfNecessary();
    }
  }
}

