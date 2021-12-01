package uk.gov.hmcts.rse.ccd.lib;

import static org.reflections.util.ConfigurationBuilder.build;


import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListener;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListenerAdapter;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import net.ttddyy.dsproxy.transform.TransformInfo;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

@Component
public class DBProxy implements BeanPostProcessor {
  enum project {
    datastore,
    definitionstore,
    userprofile,
    am,
    unknown
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

    project detectSchema(StackWalker.StackFrame stackFrame) {
      var p = stackFrame.getDeclaringClass().getPackageName();
      if (p.startsWith("uk.gov.hmcts.ccd.definition")) {
        return project.definitionstore;
      }
      if (p.startsWith("uk.gov.hmcts.ccd")) {
        return project.datastore;
      }
      if (p.startsWith("uk.gov.hmcts.reform.roleassignment")) {
        return project.am;
      }
      return project.unknown;
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
              s -> s.map(this::detectSchema)
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
