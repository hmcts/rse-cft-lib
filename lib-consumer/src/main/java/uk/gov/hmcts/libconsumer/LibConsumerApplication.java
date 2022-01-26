package uk.gov.hmcts.libconsumer;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import uk.gov.hmcts.rse.ccd.lib.SecurityConfiguration;
import uk.gov.hmcts.rse.ccd.lib.v2.lib.ParentContextConfiguration;
import uk.gov.hmcts.rse.ccd.lib.v2.data.BootData;
import uk.gov.hmcts.rse.ccd.lib.v2.definition.BootDef;

@SpringBootApplication
public class LibConsumerApplication {

	public static void main(String[] args) {
//		SpringApplication.run(LibConsumerApplication.class, args);
      new SpringApplicationBuilder()
          .parent(ParentContextConfiguration.class).web(WebApplicationType.NONE)
          .child(BootDef.DefinitionStore.class, SecurityConfiguration.class).web(WebApplicationType.SERVLET)
          .sibling(BootData.class, SecurityConfiguration.class).web(WebApplicationType.SERVLET)
          .run(args);
	}

}
