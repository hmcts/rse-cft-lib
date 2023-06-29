package uk.gov.hmcts.libconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = {"uk.gov.hmcts.reform.idam"})
@SpringBootApplication
public class LibConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibConsumerApplication.class, args);
        System.out.println("Lib consumer test app running!");
        // Shut down so we can run in automated test.
        Runtime.getRuntime().halt(0);
    }
}
