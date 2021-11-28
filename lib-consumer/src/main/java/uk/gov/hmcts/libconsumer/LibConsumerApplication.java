package uk.gov.hmcts.libconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;

@PropertySource(value = {
		"classpath:definitionstore/application.properties",
		"classpath:datastore/application.properties",
		"classpath:userprofile/application.properties",
})
@SpringBootApplication
public class LibConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LibConsumerApplication.class, args);
	}

}
