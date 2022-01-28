package uk.gov.hmcts.libconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import uk.gov.hmcts.reform.idam.client.IdamApi;

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
