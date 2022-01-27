package uk.gov.hmcts.rse.ccd.lib.common;

import javax.sql.DataSource;
import lombok.SneakyThrows;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListenerAdapter;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.ComposeRunner;

@Component
public class DBWaiter implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName) throws
      BeansException {
    if (bean instanceof DataSource) {
      ProxyFactory factory = new ProxyFactory(bean);
      factory.setProxyTargetClass(true);
      factory.addAdvice(new ProxyDataSourceInterceptor((DataSource) bean));
      return factory.getProxy();
    }
    return bean;
  }

  private static class ProxyDataSourceInterceptor extends JdbcLifecycleEventListenerAdapter
      implements MethodInterceptor {
    private final DataSource dataSource;

    public ProxyDataSourceInterceptor(final DataSource dataSource) {
      this.dataSource = ProxyDataSourceBuilder
          .create(dataSource)
          .listener(this)
          .build();
    }


    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
      checkDBIsReady();
      return invocation.proceed();
    }

    @SneakyThrows
    private void checkDBIsReady() {
      if (!initialised) {
        ComposeRunner.DB_READY.await();
        initialised = true;
      }
    }
    boolean initialised;
  }
}
