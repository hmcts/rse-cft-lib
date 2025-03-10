package uk.gov.hmcts.reform.roleassignment.befta;

import io.cucumber.java.Scenario;
import uk.gov.hmcts.befta.featuretoggle.DefaultMultiSourceFeatureToggleService;
import uk.gov.hmcts.befta.featuretoggle.FeatureToggleService;
import uk.gov.hmcts.befta.featuretoggle.ScenarioFeatureToggleInfo;

import java.util.stream.Stream;

public class RasDefaultMultiSourceFeatureToggleService extends DefaultMultiSourceFeatureToggleService {

    private static final String LAUNCH_DARKLY_FLAG = "FeatureToggle";
    public static final RasDefaultMultiSourceFeatureToggleService INSTANCE
        = new RasDefaultMultiSourceFeatureToggleService();

    @Override
    @SuppressWarnings("unchecked")
    public ScenarioFeatureToggleInfo getToggleStatusFor(Scenario toggleable) {
        ScenarioFeatureToggleInfo scenarioFeatureToggleInfo = new ScenarioFeatureToggleInfo();
        //@FeatureToggle(LD:feature_id_1=on) @FeatureToggle(RAS:feature_id_2=off)
        toggleable.getSourceTagNames().stream().filter(tag -> tag.contains(LAUNCH_DARKLY_FLAG)).forEach(tag -> {
            String domain = null;
            String id = null;
            domain = tag.contains(COLON) ? tag.substring(tag.indexOf("(") + 1, tag.indexOf(COLON)) : "LD";
            FeatureToggleService service = getToggleService(domain);

            if (!tag.contains(COLON) && !tag.contains(STRING_EQUALS)) {
                id = tag.substring(tag.indexOf("(") + 1, tag.indexOf(")"));
            } else if (tag.contains(COLON) && !tag.contains(STRING_EQUALS)) {
                id = tag.substring(tag.indexOf(COLON) + 1, tag.indexOf(")"));
            } else if (tag.contains(COLON) && tag.contains(STRING_EQUALS)) {
                id = tag.substring(tag.indexOf(COLON) + 1, tag.indexOf(STRING_EQUALS));
            }
            Boolean expectedStatus = null;
            if (tag.contains(STRING_EQUALS)) {
                var expectedStatusString = tag.substring(tag.indexOf(STRING_EQUALS) + 1, tag.indexOf(")"));
                expectedStatus = expectedStatusString.equalsIgnoreCase("on");
                scenarioFeatureToggleInfo.addExpectedStatus(id, expectedStatus);
            }
            Boolean actualStatus;
            actualStatus = (Boolean) service.getToggleStatusFor(id);
            scenarioFeatureToggleInfo.addActualStatus(id, actualStatus);
        });
        return scenarioFeatureToggleInfo;
    }

    @Override
    protected FeatureToggleService getToggleService(String toggleDomain) {
        if (Stream.of("IAC", "RAS", "DB").anyMatch(toggleDomain::equalsIgnoreCase)) {
            return new RasFeatureToggleService();
        } else if (Stream.of("EV").anyMatch(toggleDomain::equalsIgnoreCase)) {
            return new RasEnvironmentVariableToggleService();
        } else {
            return super.getToggleService(toggleDomain);
        }
    }
}
