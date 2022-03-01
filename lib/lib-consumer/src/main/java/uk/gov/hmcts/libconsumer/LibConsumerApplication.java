package uk.gov.hmcts.libconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LibConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibConsumerApplication.class, args);
        System.out.println("Lib consumer test app running!");
    }
}
