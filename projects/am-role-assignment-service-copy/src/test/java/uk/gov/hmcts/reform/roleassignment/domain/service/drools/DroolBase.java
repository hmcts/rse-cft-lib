package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfig;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.RetrieveDataService;
import uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.CIVIL_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.EMPLOYMENT_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.EMPLOYMENT_EW_MLT_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.EMPLOYMENT_SCTL_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.EMPLOYMENT_SCTL_MLT_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.PRIVATE_LAW_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.PRIVATE_LAW_EXC_RECORD_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.PUBLIC_LAW_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.SSCS_CASE_ID;
import static uk.gov.hmcts.reform.roleassignment.feignclients.configuration.DataStoreApiFallback.ST_CIC_CASE_ID;


public abstract class DroolBase {

    StatelessKieSession kieSession;
    AssignmentRequest assignmentRequest;
    List<Object> facts;
    List<FeatureFlag> featureFlags;

    public static final String IA_CASE_ID = "1234567890123450";

    public static final String CASE_REGION = DataStoreApiFallback.CASE_REGION;
    public static final String CASE_LOCATION = DataStoreApiFallback.CASE_LOCATION;

    private final RetrieveDataService retrieveDataService = mock(RetrieveDataService.class);

    Map<String, Case> caseMap = Map.ofEntries(
                                 Map.entry("IA", Case.builder()
                                           .id(IA_CASE_ID)
                                           .jurisdiction("IA")
                                           .caseTypeId("Asylum")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("SSCS", Case.builder()
                                           .id(SSCS_CASE_ID)
                                           .jurisdiction("SSCS")
                                           .caseTypeId("Benefit")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("CIVIL", Case.builder()
                                           .id(CIVIL_CASE_ID)
                                           .jurisdiction("CIVIL")
                                           .caseTypeId("CIVIL")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("PRIVATELAW", Case.builder()
                                           .id(PRIVATE_LAW_CASE_ID)
                                           .jurisdiction("PRIVATELAW")
                                           .caseTypeId("PRLAPPS")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("PRIVATELAW|PRIVATELAW_ExceptionRecord", Case.builder()
                                           .id(PRIVATE_LAW_EXC_RECORD_CASE_ID)
                                           .jurisdiction("PRIVATELAW")
                                           .caseTypeId("PRIVATELAW_ExceptionRecord")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("PUBLICLAW", Case.builder()
                                           .id(PUBLIC_LAW_CASE_ID)
                                           .jurisdiction("PUBLICLAW")
                                           .caseTypeId("CARE_SUPERVISION_EPO")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("EMPLOYMENT", Case.builder()
                                           .id(EMPLOYMENT_CASE_ID)
                                           .jurisdiction("EMPLOYMENT")
                                           .caseTypeId("ET_EnglandWales")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("EMPLOYMENT|ET_EnglandWales_Multiple", Case.builder()
                                           .id(EMPLOYMENT_EW_MLT_CASE_ID)
                                           .jurisdiction("EMPLOYMENT")
                                           .caseTypeId("ET_EnglandWales_Multiple")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("EMPLOYMENT|ET_Scotland", Case.builder()
                                           .id(EMPLOYMENT_SCTL_CASE_ID)
                                           .jurisdiction("EMPLOYMENT")
                                           .caseTypeId("ET_Scotland")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("EMPLOYMENT|ET_Scotland_Multiple", Case.builder()
                                           .id(EMPLOYMENT_SCTL_MLT_CASE_ID)
                                           .jurisdiction("EMPLOYMENT")
                                           .caseTypeId("ET_Scotland_Multiple")
                                           .data(buildDataWithRegion())
                                           .build()),

                                 Map.entry("ST_CIC", Case.builder()
                                           .id(ST_CIC_CASE_ID)
                                           .jurisdiction("ST_CIC")
                                           .caseTypeId("CriminalInjuriesCompensation")
                                           .data(buildDataWithRegion())
                                           .build())
    );

    @BeforeEach
    public void setUp() {

        //list of facts
        facts = new ArrayList<>();
        featureFlags = new ArrayList<>();

        //build assignmentRequest
        assignmentRequest = TestDataBuilder.getAssignmentRequest().build();

        //mock the retrieveDataService to fetch the Case Object
        DataStoreApiFallback dummyCases = new DataStoreApiFallback();

        // mock case data calls for all the test cases in the case map
        for (Case testCase : caseMap.values()) {
            doReturn(dummyCases.getCaseDataV2(testCase.getId()))
                .when(retrieveDataService).getCaseById(testCase.getId());
        }

        Case caseObj0 = Case.builder().id("9234567890123456")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .securityClassification(Classification.PRIVATE)
            .build();
        doReturn(caseObj0).when(retrieveDataService).getCaseById("9234567890123456");

        //mock the retrieveDataService to fetch the Case Object with incorrect type ID
        Case caseObj1 = Case.builder().id("1234567890123457")
            .caseTypeId("Not Asylum")
            .jurisdiction("IA")
            .securityClassification(Classification.PUBLIC)
            .build();
        doReturn(caseObj1).when(retrieveDataService).getCaseById("1234567890123457");

        Case caseObj3 = Case.builder().id("1234567890123459")
            .jurisdiction("CMC")
            .caseTypeId("Asylum")
            .securityClassification(Classification.PUBLIC)
            .build();
        doReturn(caseObj3).when(retrieveDataService).getCaseById("1234567890123459");

        // Set up the rule engine for validation.
        KieServices ks = KieServices.Factory.get();
        KieContainer kieContainer = ks.getKieClasspathContainer();
        this.kieSession = kieContainer.newStatelessKieSession("role-assignment-validation-session");
        this.kieSession.setGlobal("DATA_SERVICE", retrieveDataService);

    }

    Case getCaseFromMap(String jurisdiction, String caseType) {
        String key = jurisdiction + "|" + caseType;
        if (!caseMap.containsKey(key)) {
            // fallback to search on jurisdiction only
            key = jurisdiction;
        }
        return caseMap.get(key);
    }

    void buildExecuteKieSession() {
        executeDroolRules(Collections.emptyList());
    }

    void executeDroolRules(List<ExistingRoleAssignment> existingRoleAssignments) {
        // facts must contain the role config, for access to the patterns
        facts.add(RoleConfig.getRoleConfig());
        // facts must contain all affected role assignments
        facts.addAll(assignmentRequest.getRequestedRoles());

        // facts must contain all existing role assignments
        facts.addAll(existingRoleAssignments);

        // facts must contain the request
        facts.add(assignmentRequest.getRequest());

        facts.addAll(featureFlags);

        // Run the rules
        kieSession.execute(facts);

        //flush the facts/flags so parameterised tests can run multiple executions
        facts.clear();
        featureFlags.clear();
    }

    private Map<String, JsonNode> buildDataWithRegion() {
        return Map.of(
            Case.CASE_MANAGEMENT_LOCATION,
            JacksonUtils.convertValueJsonNode(
                Map.of(
                    Case.REGION, JacksonUtils.convertValueJsonNode(CASE_REGION),
                    Case.BASE_LOCATION, JacksonUtils.convertValueJsonNode(CASE_LOCATION)
                )
            )
        );
    }

    public RetrieveDataService getRetrieveDataService() {
        return retrieveDataService;
    }

}
