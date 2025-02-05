package uk.gov.hmcts.reform.roleassignment.launchdarkly;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

@Service
public class FeatureToggleService {

    public static final String USER = "user";
    public static final String SERVICE_NAME = "servicename";
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String DELETE = "DELETE";

    @Autowired
    private LDClientInterface ldClient;

    @Value("${launchdarkly.sdk.environment}")
    private String environment;

    @Value("${launchdarkly.sdk.user}")
    private String userName;
    private static final HashMap<String, String> getRequestMap = new HashMap<>();
    private static final HashMap<String, String> postRequestMap = new HashMap<>();

    static {
        //Any new end point need to be placed in respective map.
        getRequestMap.put("/am/role-assignments/fetchFlagStatus","get-db-drools-flag");
        postRequestMap.put("/am/role-assignments/createFeatureFlag","get-db-drools-flag");
        postRequestMap.put("/am/role-assignments/query/delete","advance_delete_api_flag");
    }

    public boolean isFlagEnabled(String serviceName, String flagName) {
        LDUser user = new LDUser.Builder(environment)
            .firstName(userName)
            .lastName(USER)
            .custom(SERVICE_NAME, serviceName)
            .build();

        return ldClient.boolVariation(flagName, user, false);
    }

    public boolean isValidFlag(String flagName) {
        return ldClient.isFlagKnown(flagName);
    }

    public String getLaunchDarklyFlag(HttpServletRequest request) {
        var uri = request.getRequestURI();
        switch (request.getMethod()) {
            case GET:
                if (getRequestMap.get(uri) != null) {
                    return getRequestMap.get(uri);
                }
                break;
            case POST:
                if (postRequestMap.get(uri) != null) {
                    return postRequestMap.get(uri);
                }
                break;
            case DELETE:
                break;
            default:
        }
        return null;
    }
}
