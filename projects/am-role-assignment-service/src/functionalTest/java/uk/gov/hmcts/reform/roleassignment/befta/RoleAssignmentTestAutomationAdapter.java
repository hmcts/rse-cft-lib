package uk.gov.hmcts.reform.roleassignment.befta;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;
import uk.gov.hmcts.reform.roleassignment.befta.utils.TokenUtils;
import uk.gov.hmcts.reform.roleassignment.befta.utils.UserTokenProviderConfig;
import uk.gov.hmcts.reform.roleassignment.util.EnvironmentVariableUtils;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class RoleAssignmentTestAutomationAdapter extends DefaultTestAutomationAdapter {
    public static RoleAssignmentTestAutomationAdapter INSTANCE = new RoleAssignmentTestAutomationAdapter();

    @Override
    public Object calculateCustomValue(BackEndFunctionalTestScenarioContext scenarioContext, Object key) {
        //the docAMUrl is is referring the self link in PR
        switch (key.toString()) {
            case ("generateUUID"):
                return UUID.randomUUID();
            case ("generateCaseId"):
                return generateCaseId();
            case ("generateS2STokenForCcd"):
                return new TokenUtils().generateServiceToken(buildCcdSpecificConfig());
            case ("generateS2STokenForXui"):
                return new TokenUtils().generateServiceToken(buildXuiSpecificConfig());
            case ("generateS2STokenForOrm"):
                return new TokenUtils().generateServiceToken(buildOrmSpecificConfig());
            case ("tomorrow"):
                return LocalDate.now().plusDays(1);
            case ("today"):
                return LocalDate.now();
            default:
                return super.calculateCustomValue(scenarioContext, key);
        }
    }

    private Object generateCaseId() {
        var currentTime = new Date().getTime();
        var time = Long.toString(currentTime);
        return time + ("0000000000000000".substring(time.length()));
    }

    private UserTokenProviderConfig buildCcdSpecificConfig() {
        UserTokenProviderConfig config = new UserTokenProviderConfig();
        config.setMicroService("ccd_data");
        config.setSecret(System.getenv("CCD_DATA_S2S_SECRET"));
        config.setS2sUrl(EnvironmentVariableUtils.getRequiredVariable("IDAM_S2S_URL"));
        return config;
    }

    private UserTokenProviderConfig buildXuiSpecificConfig() {
        UserTokenProviderConfig config = new UserTokenProviderConfig();
        config.setMicroService("xui_webapp");
        config.setSecret(System.getenv("XUI_WEBAPP_S2S_SECRET"));
        config.setS2sUrl(EnvironmentVariableUtils.getRequiredVariable("IDAM_S2S_URL"));
        return config;
    }

    private UserTokenProviderConfig buildOrmSpecificConfig() {
        UserTokenProviderConfig config = new UserTokenProviderConfig();
        config.setMicroService("am_org_role_mapping_service");
        config.setSecret(System.getenv("AM_ORG_S2S_SECRET"));
        config.setS2sUrl(EnvironmentVariableUtils.getRequiredVariable("IDAM_S2S_URL"));
        return config;
    }
}
