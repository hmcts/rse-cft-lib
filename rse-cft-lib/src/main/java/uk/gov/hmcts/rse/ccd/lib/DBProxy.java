package uk.gov.hmcts.rse.ccd.lib;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sql.DataSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListenerAdapter;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

@Slf4j
@Component
class DBProxy implements BeanPostProcessor {
  private static volatile String applicationPackage;
  private static BlockingQueue<LibInfo> queue = new LinkedBlockingQueue<>();

  @AllArgsConstructor
  @Data
  static class LibInfo {
    int dbPort;
    int esPort;
  }

  public static class RunListener implements SpringApplicationRunListener {
    public RunListener(SpringApplication app, String[] args) {
      if (applicationPackage == null) {
        // TODO
        DBProxy.applicationPackage = app.getMainApplicationClass().getPackageName();
        new Thread(this::dockerBoot).start();
      }
    }

    @SneakyThrows
    void dockerBoot() {
      var f = File.createTempFile("cftlib", "");
      URL u = getClass().getResource("/rse/cftlib-docker-compose.yml");
      FileUtils.copyURLToFile(u, f);

      var environment = Map.of("COMPOSE_FILE", f.getName());
      new ProcessExecutor().command("docker-compose", "up", "-d")
          .redirectOutput(Slf4jStream.of(log).asInfo())
          .redirectError(Slf4jStream.of(log).asInfo())
          .directory(f.getParentFile())
          .environment(environment)
          .exitValueNormal()
          .executeNoTimeout();

      Callable<Boolean> ready = () -> {
        try (Socket socket = new Socket()) {
          InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", 6432);
          socket.connect(inetSocketAddress, 1000);
        } catch (IOException e) {
          throw new IllegalStateException("DB not ready yet");
        }
        return true;
      };
      Awaitility.await()
          .pollInSameThread()
          .pollInterval(Duration.ofMillis(100))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .forever()
          .until(ready);

      Thread.sleep(10000);
      queue.put(new LibInfo(6432, 9200));
    }
  }

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
    private final HikariDataSource hikari;

    public ProxyDataSourceInterceptor(final DataSource dataSource) {
      this.hikari = (HikariDataSource) dataSource;
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
        checkDBIsReady();
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

    @SneakyThrows
    private void checkDBIsReady() {
      if (!initialised) {
        var info = queue.take();
        hikari.setJdbcUrl(String.format("jdbc:postgresql://localhost:%s/postgres?stringtype=unspecified", info.getDbPort()));
        initialised = true;
      }
    }
    boolean initialised;
  }
}
