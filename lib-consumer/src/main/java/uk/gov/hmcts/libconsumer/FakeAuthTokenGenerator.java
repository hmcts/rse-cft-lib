package uk.gov.hmcts.libconsumer;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

@Primary
@Component
public class FakeAuthTokenGenerator implements AuthTokenGenerator {
  @Override
  public String generate() {
    return "todo";
  }
}
