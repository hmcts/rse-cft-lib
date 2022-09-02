package uk.gov.hmcts.rse.ccd.lib.model;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;

@ConditionalOnClass(CaseDataAPIApplication.class)
@Component
public class BeanSwapper implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory.containsBean("spreadsheetParser")) {
            ((DefaultListableBeanFactory) beanFactory).removeBeanDefinition("spreadsheetParser");
        }
    }
}
