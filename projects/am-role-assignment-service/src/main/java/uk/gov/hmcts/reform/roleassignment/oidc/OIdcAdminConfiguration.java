package uk.gov.hmcts.reform.roleassignment.oidc;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class OIdcAdminConfiguration {

    private String userId;
    private String secret;
    private String scope;

    @Autowired
    public OIdcAdminConfiguration(
        @Value("${idam.client.admin.userId:}") String userId,
        @Value("${idam.client.admin.secret:}") String secret,
        @Value("${idam.client.admin.scope:}") String scope
    ) {
        this.userId = userId;
        this.secret = secret;
        this.scope = scope;
    }

}
