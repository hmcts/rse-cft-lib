package uk.gov.hmcts.divorce.sow014.possessions;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.Permission;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;

import static uk.gov.hmcts.divorce.common.ccd.CcdPageConfiguration.NEVER_SHOW;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;

@Component
public class PendingApplications implements CCDConfig<CaseData, State, UserRole> {

    @Override
    public void configure(ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        configBuilder.tab("pendingApplications", "Pending Applications")
            .forRoles(UserRole.JUDGE)
            .label("pendingApplications", null, "${pendingApplicationsMd}")
            .field("pendingApplicationsMd", NEVER_SHOW)
            .build();

        configBuilder.tab("Claims", "Claims")
            .forRoles(UserRole.JUDGE)
            .label("claims", null, "${claimsMd}")
            .field("claimsMd", NEVER_SHOW)
            .build();

        configBuilder.tab("yourClients", "Your Clients")
            .forRoles(UserRole.SOLICITOR)
            .label("clients", null, "${clientsMd}")
            .field("clientsMd", NEVER_SHOW)
            .build();

        addInterestedParty(configBuilder);
    }

    private void addInterestedParty(ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        configBuilder.event("addInterestedParty")
            .forAllStates()
            .name("Add interested party")
            .grant(CREATE_READ_UPDATE, UserRole.SOLICITOR, UserRole.CASE_WORKER)
            .grantHistoryOnly(UserRole.JUDGE)
            .fields()
            .page("addInterestedParty")
            .mandatory(CaseData::getInterestedPartyForename);
    }
}
