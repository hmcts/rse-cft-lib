package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

@Component
public class FakeAuthTokenGenerator implements AuthTokenGenerator {
  @Override
  public String generate() {
    return "todo";
  }
}
