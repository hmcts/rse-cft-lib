package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;
import uk.gov.hmcts.reform.authorisation.generators.ServiceAuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;

@Configuration
class IdamConfig {

  @ForProjects({DBProxy.project.datastore, DBProxy.project.definitionstore})
  @Bean
  public IdamClient ccdIdam(IdamApi idam,
                            @Value("${IDAM_OAUTH2_DATA_STORE_CLIENT_SECRET:idam_data_store_client_secret}") String secret
  ) {
    var oauth = new OAuth2Configuration(
        "http://ccd-data-store-api/oauth2redirect",
        "ccd_data_store_api",
        secret,
        "profile openid roles manage-user"
    );

    return new IdamClient(idam, oauth);
  }

  // This is the idam client that would normally be Autoconfigured,
  // injectable only into the actual application.
  @ForProjects(DBProxy.project.application)
  @Bean
  public IdamClient defaultIdam(IdamApi idam, OAuth2Configuration oauth) {
    return new IdamClient(idam, oauth);
  }

  @ForProjects(DBProxy.project.am)
  @Bean
  public ServiceAuthTokenGenerator cftlibAMS2S(
      @Value("${AM_ROLE_ASSIGNMENT_SERVICE_SECRET:AAAAAAAAAAAAAAAA}") final String secret,
      final ServiceAuthorisationApi serviceAuthorisationApi) {
    return new ServiceAuthTokenGenerator(secret, "am_role_assignment_service", serviceAuthorisationApi);
  }

  @ForProjects(DBProxy.project.datastore)
  @Bean
  public ServiceAuthTokenGenerator cftlibDatastoreS2S(
      @Value("${DATA_STORE_IDAM_KEY:AAAAAAAAAAAAAAAA}") final String secret,
      final ServiceAuthorisationApi serviceAuthorisationApi) {
    return new ServiceAuthTokenGenerator(secret, "ccd_data", serviceAuthorisationApi);
  }

  @ForProjects(DBProxy.project.definitionstore)
  @Bean
  public ServiceAuthTokenGenerator cftlibDefstoreS2S(
      @Value("${DEFINITION_STORE_IDAM_KEY:AAAAAAAAAAAAAAAA}") final String secret,
      final ServiceAuthorisationApi serviceAuthorisationApi) {
    return new ServiceAuthTokenGenerator(secret, "ccd_definition", serviceAuthorisationApi);
  }
}
