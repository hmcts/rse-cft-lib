package uk.gov.hmcts.reform.roleassignment.controller;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.BaseTest;
import uk.gov.hmcts.reform.roleassignment.MockUtils;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.UserRoles;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.RetrieveDataService;
import uk.gov.hmcts.reform.roleassignment.domain.service.security.IdamRoleService;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.launchdarkly.FeatureConditionEvaluation;
import uk.gov.hmcts.reform.roleassignment.oidc.IdamRepository;
import uk.gov.hmcts.reform.roleassignment.util.Constants;

import javax.inject.Inject;
import javax.sql.DataSource;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.STANDARD;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedOrgRole;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@TestPropertySource(properties = {"org.request.byPassOrgDroolRule=false", "dbFeature.flags.enable=iac_jrd_1_0"})
public class DroolPassAssignmentCreateAndDeleteIntegrationTest extends BaseTest {

    private static final Logger logger =
        LoggerFactory.getLogger(DroolPassAssignmentCreateAndDeleteIntegrationTest.class);

    private static final String AUTHORISED_SERVICE = "ccd_gw";

    private MockMvc mockMvc;

    @Inject
    private WebApplicationContext wac;

    @MockBean
    private IdamRepository idamRepository;

    @Autowired
    private DataSource ds;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @MockBean
    private RetrieveDataService retrieveDataService;

    @MockBean
    private IdamRoleService idamRoleService;

    @MockBean
    private FeatureConditionEvaluation featureConditionEvaluation;

    @Before
    public void setUp() throws Exception {

        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        MockitoAnnotations.openMocks(this);
        var uid = "6b36bfc6-bb21-11ea-b3de-0242ac130006";
        UserRoles roles = UserRoles.builder()
            .uid(uid)
            .roles(List.of("caseworker", "am-import"))
            .build();

        doReturn(roles).when(idamRoleService).getUserRoles(anyString());
        doReturn(authentication).when(securityContext).getAuthentication();
        SecurityContextHolder.setContext(securityContext);
        doReturn(true).when(featureConditionEvaluation).preHandle(any(),any(),any());
        MockUtils.setSecurityAuthorities(authentication, MockUtils.ROLE_CASEWORKER);
        UserInfo userInfo = UserInfo.builder()
            .uid("6b36bfc6-bb21-11ea-b3de-0242ac130006")
            .sub("emailId@a.com")
            .build();
        doReturn(userInfo).when(idamRepository).getUserInfo(anyString());
        Case retrievedCase = Case.builder().id("1234567890123456")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        doReturn(retrievedCase).when(retrieveDataService).getCaseById(anyString());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql"

        })
    public void shouldRejectRoleAssignmentsWithWrongClientId() throws Exception {
        AssignmentRequest assignmentRequest = buildDroolRuleBypassRequest();
        assignmentRequest.getRequest().setClientId("wrong_am_org_role_mapping_service");
        logger.info(" assignmentRequest :  {}", mapper.writeValueAsString(assignmentRequest));
        final var url = "/am/role-assignments";


        mockMvc.perform(post(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .content(mapper.writeValueAsBytes(assignmentRequest))
        ).andExpect(status().is(422))
         .andExpect(jsonPath("$.roleAssignmentResponse.roleRequest.status").value("REJECTED"))
         .andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql"
        })
    public void shouldCreateRoleAssignmentsWithCorrectClientId() throws Exception {
        AssignmentRequest assignmentRequest = buildDroolRuleBypassRequest();
        logger.info(" assignmentRequest :  {}", mapper.writeValueAsString(assignmentRequest));
        final var url = "/am/role-assignments";


        mockMvc.perform(post(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders("am_org_role_mapping_service"))
                            .content(mapper.writeValueAsBytes(assignmentRequest))
        ).andExpect(status().is(201)).andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_assignment_records_to_delete.sql"})
    public void shouldDeleteRoleAssignmentsByProcessAndReference() throws Exception {
        final var url = "/am/role-assignments";

        mockMvc.perform(delete(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders("wrong"))
                            .param("process", "S-052")
                            .param("reference", "S-052")
        )
            .andExpect(status().is(204))
            .andReturn();
    }

    private AssignmentRequest buildDroolRuleBypassRequest() {
        final AssignmentRequest assignmentRequest =
            TestDataBuilder.createRoleAssignmentRequest(
            true,
            true
        );
        assignmentRequest.getRequest().setRequestType(RequestType.CREATE);
        assignmentRequest.getRequest().setStatus(CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });

        return assignmentRequest;
    }

    @NotNull
    private HttpHeaders getHttpHeaders() {
        return getHttpHeaders(AUTHORISED_SERVICE);
    }

    @NotNull
    private HttpHeaders getHttpHeaders(final String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTHORIZATION, "Bearer user1");
        var s2SToken = MockUtils.generateDummyS2SToken(clientId);
        headers.add("ServiceAuthorization", "Bearer " + s2SToken);
        headers.add(Constants.CORRELATION_ID_HEADER_NAME, "38a90097-434e-47ee-8ea1-9ea2a267f51d");
        return headers;
    }
}
