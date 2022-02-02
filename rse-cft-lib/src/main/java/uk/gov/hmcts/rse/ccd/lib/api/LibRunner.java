package uk.gov.hmcts.rse.ccd.lib.api;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.rse.ccd.lib.impl.BootAccessManagement;
import uk.gov.hmcts.rse.ccd.lib.impl.BootData;
import uk.gov.hmcts.rse.ccd.lib.impl.BootDef;
import uk.gov.hmcts.rse.ccd.lib.impl.BootParent;
import uk.gov.hmcts.rse.ccd.lib.impl.BootUserProfile;
import uk.gov.hmcts.rse.ccd.lib.impl.PermissiveSecurity;
import uk.gov.hmcts.rse.ccd.lib.impl.Project;
import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;
import uk.gov.hmcts.rse.ccd.lib.impl.YamlPropertySourceFactory;

public class LibRunner {
  private Map<Project, ConfigurableApplicationContext> contexts = Maps.newConcurrentMap();

  private final Class application;
  private final Class[] inject;
  public LibRunner(Class application, Class... inject) {
    this.application = application;
    this.inject = inject;
  }

  public Map<Project, WebApplicationContext> run() {
    return run(Map.of());
  }

  public Map<Project, WebApplicationContext> run(Map<String, Object> propertyOverrides) {
    var classes = new ArrayList<Class>();
    classes.add(BootParent.class);
    classes.addAll(Arrays.asList(inject));

    final SpringApplication parentApplication = new SpringApplication(classes.toArray(new Class[0]));
    parentApplication.setWebApplicationType(WebApplicationType.NONE);
    var parentContext = parentApplication.run( "" );
    final ParentContextApplicationContextInitializer parentContextApplicationContextInitializer = new ParentContextApplicationContextInitializer( parentContext );

    Map<Project, List<Class>> childContexts = Map.of(
        // TODO - auto-auto spring security config.
        Project.Application, List.of(application, CFTLib.class, PermissiveSecurity.class),
        Project.AM, List.of(BootAccessManagement.class),
        Project.Definitionstore, List.of(BootDef.class),
        Project.Userprofile, List.of(BootUserProfile.class),
        Project.Datastore, List.of(BootData.class)
    );

    Map<Project, WebApplicationContext> contexts = Maps.newConcurrentMap();
    childContexts.keySet().parallelStream().sorted().forEach(project -> {
      System.out.println("Starting " + project);
      var name = Thread.currentThread().getName();
      Thread.currentThread().setName("**** " + project);
      final SpringApplication a = new SpringApplication(childContexts.get(project).toArray(new Class[0]));
      a.addInitializers( parentContextApplicationContextInitializer );
      a.setBannerMode(Banner.Mode.OFF);

      final StandardEnvironment environment = new StandardEnvironment( );
      a.setEnvironment(environment);

      var sources = environment.getPropertySources();

      // Shut off unwanted autoconfiguration.
      if (project == Project.Application) {
        final Map<String, Object> properties = Map.of( "spring.autoconfigure.exclude",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            // Disable a problematic cdam healthcheck.
            "management.health.case-document-am-api.enabled", "false"
        );
        environment.getPropertySources().addFirst( new MapPropertySource( "Autoconfig exclusions", properties ) );
      } else {
        tryAddProperties(sources, project.name() + "-base", project.name().toLowerCase() + "/application.properties");
        tryAddProperties(sources, project.name() + "-baseyaml", project.name().toLowerCase() + "/application.yaml");
        tryAddProperties(sources, project.name() + "-rse", "rse/application.properties");
        tryAddProperties(sources, project.name() + "-specific", "rse/" + project.name().toLowerCase() + ".properties");
      }

      sources.addFirst(new MapPropertySource("applicationOverrides", propertyOverrides));
      contexts.put(project, (WebApplicationContext) a.run());
      Thread.currentThread().setName(name);
    });

    var userprofile = contexts.get(Project.Userprofile).getBean(UserProfileEndpoint.class);
    var roleController = contexts.get(Project.Definitionstore).getBean(UserRoleController.class);
    var lib = contexts.get(Project.Application).getBean(CFTLib.class);
    var amDB = contexts.get(Project.AM).getBean(DataSource.class);
    lib.init(roleController, userprofile, amDB);

    return contexts;
  }

  @SneakyThrows
  void tryAddProperties(MutablePropertySources sources, String name, String path) {
    if (new ClassPathResource(path).exists()) {
      sources.addFirst(loadClasspathProperties(name, path));
    }
  }

  @SneakyThrows
  PropertySource<?> loadClasspathProperties(String name, String path) {
    if (path.endsWith("yaml")) {
      return new YamlPropertySourceFactory().createPropertySource(name, new EncodedResource(new ClassPathResource(path)));
    }
    return new PropertiesPropertySource(name, PropertiesLoaderUtils.loadAllProperties(path));
  }

}
