package uk.gov.hmcts.libconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.common.SecurityConfiguration;
import uk.gov.hmcts.rse.ccd.lib.v2.lib.ParentContextConfiguration;
import uk.gov.hmcts.rse.ccd.lib.v2.data.BootData;
import uk.gov.hmcts.rse.ccd.lib.v2.definition.BootDef;
import uk.gov.hmcts.rse.ccd.lib.v2.profile.BootUserProfile;

@EnableFeignClients(
    basePackageClasses = {
        IdamApi.class
    }
)
@SpringBootApplication
public class LibConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LibConsumerApplication.class, args);
//      new SpringApplicationBuilder()
//          .parent(ParentContextConfiguration.class).web(WebApplicationType.NONE)
////          .child(LibConsumerApplication.class).web(WebApplicationType.SERVLET)
//          .child(BootDef.class).web(WebApplicationType.SERVLET)
//          .sibling(BootUserProfile.class).web(WebApplicationType.SERVLET)
//          .sibling(BootData.class).web(WebApplicationType.SERVLET)
//          .run(args);
	}

}
