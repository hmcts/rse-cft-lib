package uk.gov.hmcts.rse.ccd.lib.api;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.elasticsearch.common.util.set.Sets;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.definition.store.rest.endpoint.UserRoleController;
import uk.gov.hmcts.rse.ccd.lib.impl.BootAccessManagement;
import uk.gov.hmcts.rse.ccd.lib.impl.BootData;
import uk.gov.hmcts.rse.ccd.lib.impl.BootDef;
import uk.gov.hmcts.rse.ccd.lib.impl.BootUserProfile;
import uk.gov.hmcts.rse.ccd.lib.impl.Project;
import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;

public class LibRunner {
  private Map<Project, ConfigurableApplicationContext> contexts = Maps.newConcurrentMap();

  private final Class application;
  private final Set<Class> inject;
  public LibRunner(Class application, Class... inject) {
    this.application = application;
    this.inject = Set.of(inject);
  }

  public Map<Project, WebApplicationContext> run() {
    Map<Project, Set<Class>> childContexts = Map.of(
        Project.Application, Set.of(application, CFTLib.class),
        Project.AM, Set.of(BootAccessManagement.class),
        Project.Definitionstore, Set.of(BootDef.class),
        Project.Userprofile, Set.of(BootUserProfile.class),
        Project.Datastore, Set.of(BootData.class)
    );

    Map<Project, WebApplicationContext> contexts = Maps.newConcurrentMap();
    childContexts.keySet().parallelStream().forEach(project -> {
      System.out.println("Starting " + project);
      var name = Thread.currentThread().getName();
      Thread.currentThread().setName("**** " + project);
      var classes = Sets.union(childContexts.get(project), inject);
      final SpringApplication a = new SpringApplication(classes.toArray(new Class[0]));

      // Shut off unwanted autoconfiguration.
      if (project == Project.Application) {
        final StandardEnvironment environment = new StandardEnvironment( );
        final Map<String, Object> properties = Map.of( "spring.autoconfigure.exclude",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        );
        environment.getPropertySources().addFirst( new MapPropertySource( "Autoconfig exclusions", properties ) );
        a.setEnvironment(environment);
      }

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
}
