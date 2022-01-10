package uk.gov.hmcts.libconsumer;

import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

@Component
public class FakeS2S implements ServiceAuthorisationApi {

  @Override
  public String serviceToken(Map<String, String> signIn) {
    return "";
  }

  @Override
  public void authorise(String authHeader, String[] roles) {

  }

  @Override
  public String getServiceName(String authHeader) {
    return "";
  }
}
