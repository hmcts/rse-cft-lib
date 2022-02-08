package uk.gov.hmcts.rse.ccd.lib.test;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.SpringBootMockMvcBuilderCustomizer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.rse.ccd.lib.api.LibRunner;
import uk.gov.hmcts.rse.ccd.lib.impl.Project;

public abstract class CftLibTest {
  protected Map<Project, MockMvc> mockMVCs = Maps.newHashMap();

  @SneakyThrows
  @BeforeAll
  void setup() {
    var contexts = LibRunner.run(getApplicationClass(), getInjectedClasses(),Map.of(
            // Disable default feign client in favour of our fake.
            "idam.s2s-auth.url", "false"
        ));

    for (Project project : contexts.keySet()) {
      var context = contexts.get(project);
      var builder = MockMvcBuilders.webAppContextSetup(context);
      new SpringBootMockMvcBuilderCustomizer(context).customize(builder);

      mockMVCs.put(project, builder.apply(springSecurity())
          .build());
    }
  }

  protected abstract Class getApplicationClass();

  protected Set<Class> getInjectedClasses() {
    return Set.of(FakeS2S.class);
  }

}
