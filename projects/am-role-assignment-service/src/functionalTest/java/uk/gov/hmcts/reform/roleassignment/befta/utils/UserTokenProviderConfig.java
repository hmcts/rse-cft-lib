package uk.gov.hmcts.reform.roleassignment.befta.utils;

public class UserTokenProviderConfig {

    String secret;
    String microService;
    String s2sUrl;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getMicroService() {
        return microService;
    }

    public void setMicroService(String microService) {
        this.microService = microService;
    }

    public String getS2sUrl() {
        return s2sUrl;
    }

    public void setS2sUrl(String s2sUrl) {
        this.s2sUrl = s2sUrl;
    }

}
