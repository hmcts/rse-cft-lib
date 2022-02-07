package uk.gov.hmcts.libconsumer;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.rse.ccd.lib.api.LibRunner;

@EnableFeignClients(
    basePackageClasses = {
        IdamApi.class
    }
)
@SpringBootApplication
public class LibConsumerApplication {

  public static void main(String[] args) {
    LibRunner.run(LibConsumerApplication.class);
  }

}
