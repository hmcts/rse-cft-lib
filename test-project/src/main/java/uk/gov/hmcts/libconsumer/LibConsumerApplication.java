package uk.gov.hmcts.libconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

@SpringBootApplication
public class LibConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibConsumerApplication.class, args);
        System.out.println("Lib consumer test app running!");
        // Shut down so we can run in automated test.
        Runtime.getRuntime().halt(0);
    }

    // Register an s2s auth filter.
    @Bean
    public FilterRegistrationBean registerAuthFilter(ServiceAuthFilter serviceAuthFilter) {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(serviceAuthFilter);
        return filterRegistrationBean;
    }
}
