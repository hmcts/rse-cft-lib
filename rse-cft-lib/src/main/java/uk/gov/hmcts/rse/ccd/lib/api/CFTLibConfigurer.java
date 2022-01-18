package uk.gov.hmcts.rse.ccd.lib.api;

public interface CFTLibConfigurer {
  void configure(CFTLib lib, boolean cleanInstall) throws Exception;
}
