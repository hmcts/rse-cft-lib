package uk.gov.hmcts.libconsumer.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.idam.client.IdamClient;

// Uses IDAM and should be injected successfully
// with the default idam settings.
@Component
public class IdamDependent {
  @Autowired
  private IdamClient idamService;

}
