package uk.gov.hmcts.libconsumer;

import java.io.IOException;
import java.nio.charset.Charset;
import javax.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

@Component
public class CFTLibConfig implements CFTLibConfigurer {
  @SneakyThrows
  @Override
  public void configure(CFTLib lib) {
    lib.createProfile("a@b.com");
    lib.createRoles(
        "caseworker-divorce-courtadmin_beta",
        "caseworker-divorce-superuser",
        "caseworker-divorce-courtadmin-la",
        "caseworker-divorce-courtadmin",
        "caseworker-divorce-solicitor",
        "caseworker-divorce-pcqextractor",
        "caseworker-divorce-systemupdate",
        "caseworker-divorce-bulkscan",
        "caseworker-caa",
        "citizen"
    );
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    var json = IOUtils.toString(resourceLoader.getResource("classpath:cftlib-am-role-assignments.json").getInputStream(), Charset.defaultCharset());
    lib.configureRoleAssignments(json);

    var def = getClass().getClassLoader().getResourceAsStream("NFD-dev.xlsx").readAllBytes();
    lib.importDefinition(def);
  }
}
