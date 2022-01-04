package uk.gov.hmcts.rse.ccd.lib;

import java.nio.charset.Charset;
import java.sql.Connection;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
class RoleAssignmentConfigurer {
  @Autowired
  private DataSource data;

  @SneakyThrows
  @PostConstruct
  public void init(){
    try (Connection c = data.getConnection()) {
      // To use the uuid generation function.
      c.createStatement().execute(
          "create extension pgcrypto"
      );

      ResourceLoader resourceLoader = new DefaultResourceLoader();
      // Provided by the consuming application.
      var json = IOUtils.toString(resourceLoader.getResource("classpath:cftlib-am-role-assignments.json").getInputStream(), Charset.defaultCharset());
      var sql = IOUtils.toString(resourceLoader.getResource("classpath:rse/cftlib-populate-am.sql").getInputStream(), Charset.defaultCharset());
      var p = c.prepareStatement(sql);
      p.setString(1, json);
      p.executeQuery();
    }
  }
}
