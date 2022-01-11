package uk.gov.hmcts.libconsumer;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

@Component
public class CFTLibConfig {
  @Autowired
  CFTLib lib;

  @PostConstruct
  void configure() {
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
  }
}
