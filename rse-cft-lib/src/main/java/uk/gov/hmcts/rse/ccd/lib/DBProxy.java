package uk.gov.hmcts.rse.ccd.lib;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListenerAdapter;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

@Component
class DBProxy implements BeanPostProcessor {
  // Set by the RunListener.
  // TODO
  public static String applicationPackage;

  enum project {
    datastore,
    definitionstore,
    userprofile,
    am,
    application,
    unknown
  }

  public static project detectSchema(String p) {
    if (p.startsWith("uk.gov.hmcts.ccd.definition")) {
      return project.definitionstore;
    }
    if (p.startsWith("uk.gov.hmcts.ccd.data.userprofile")
        || p.startsWith("uk.gov.hmcts.ccd.endpoint.userprofile")) {
      return project.userprofile;
    }
    if (p.startsWith("uk.gov.hmcts.ccd")) {
      return project.datastore;
    }
    if (p.startsWith("uk.gov.hmcts.reform.roleassignment")) {
      return project.am;
    }
    if (p.startsWith(applicationPackage)) {
      return project.application;
    }
    return project.unknown;
  }

  @Override
  public Object postProcessBeforeInitialization(final Object bean, final String beanName)
      throws BeansException {
    return bean;
  }

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
      Method proxyMethod =
          ReflectionUtils.findMethod(dataSource.getClass(), invocation.getMethod().getName());
      if (proxyMethod != null) {
        Object result = proxyMethod.invoke(dataSource, invocation.getArguments());
        if (invocation.getMethod().getName().equals("getConnection")) {
          StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
          var schema = walker.walk(
              s -> s.map(x -> x.getDeclaringClass().getPackageName()).map(DBProxy::detectSchema)
                  .filter(x -> x != project.unknown)
                  .findFirst());

          if (schema.isPresent()) {
            try (Statement sql = ((Connection)result).createStatement()){
              sql.execute(String.format("set search_path to %s,public", schema.get()));
            }
          }
        }

        return result;
      }
      return invocation.proceed();
    }
  }
}
