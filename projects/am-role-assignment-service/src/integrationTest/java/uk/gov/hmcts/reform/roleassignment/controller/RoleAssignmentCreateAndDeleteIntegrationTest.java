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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.BaseTest;
import uk.gov.hmcts.reform.roleassignment.MockUtils;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.UserRoles;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.RetrieveDataService;
import uk.gov.hmcts.reform.roleassignment.domain.service.security.IdamRoleService;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.launchdarkly.FeatureConditionEvaluation;
import uk.gov.hmcts.reform.roleassignment.util.Constants;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;

@TestPropertySource(properties = {"dbFeature.flags.enable=iac_jrd_1_0"})
public class RoleAssignmentCreateAndDeleteIntegrationTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentCreateAndDeleteIntegrationTest.class);

    private static final String ASSIGNMENT_ID = "f7edb29d-e421-450c-be66-a10169b04f0a";
    private static final String ACTOR_ID = "123e4567-e89b-42d3-a456-556642445612";
    private static final String COUNT_HISTORY_RECORDS_QUERY = "SELECT count(1) AS n FROM role_assignment_history";
    private static final String COUNT_ASSIGNMENT_RECORDS_QUERY = "SELECT count(1) AS n FROM role_assignment";
    private static final String GET_ACTOR_FROM_ASSIGNMENT_QUERY = "SELECT actor_id FROM role_assignment WHERE id IN "
        + "(SELECT id FROM role_assignment_history WHERE actor_id = ?)";
    private static final String GET_ASSIGNMENT_STATUS_QUERY = "SELECT status FROM role_assignment_history "
        + "WHERE actor_id = ? ORDER BY created";
    private static final String GET_STATUS_COUNT_QUERY = "SELECT COUNT(*) FROM role_assignment_history "
        + "WHERE status =";
    private static final String ADV_DELETE_URL = "/am/role-assignments/query/delete";
    public static final String CREATED = "CREATED";
    public static final String APPROVED = "APPROVED";
    public static final String LIVE = "LIVE";
    public static final String DELETED = "DELETED";
    public static final String DELETE_APPROVED = "DELETE_APPROVED";
    private static final String AUTHORISED_SERVICE = "ccd_gw";

    private MockMvc mockMvc;
    private JdbcTemplate template;

    @Inject
    private WebApplicationContext wac;

    @MockBean
    private IdamApi idamApi;

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
        template = new JdbcTemplate(ds);
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
        doReturn(userInfo).when(idamApi).retrieveUserInfo(anyString());
        Case retrievedCase = Case.builder().id("1234567890123456")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .securityClassification(Classification.PUBLIC)
            .build();
        doReturn(retrievedCase).when(retrieveDataService).getCaseById(anyString());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        scripts = {"classpath:sql/role_assignment_clean_up.sql","classpath:sql/insert_role_assignment_to_create.sql"})
    public void shouldCreateRoleAssignmentsWithReplaceExistingTrue() throws Exception {
        logger.info(" History record count before create assignment request {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count before create assignment request {}", getAssignmentRecordsCount());
        AssignmentRequest assignmentRequest = TestDataBuilder.createRoleAssignmentRequest(
            false, false);
        assignmentRequest.getRequest().setAssignerId("6b36bfc6-bb21-11ea-b3de-0242ac130006");
        logger.info(" assignmentRequest :  {}", mapper.writeValueAsString(assignmentRequest));
        final var url = "/am/role-assignments";


        mockMvc.perform(post(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .content(mapper.writeValueAsBytes(assignmentRequest))
        ).andExpect(status().is(201)).andReturn();

        logger.info(" -- Role Assignment record created successfully -- ");
        List<String> statusList = getStatusFromHistory();
        assertNotNull(statusList);
        assertEquals(3, statusList.size());
        assertEquals(CREATE_REQUESTED.toString(), statusList.get(0));
        assertEquals(APPROVED, statusList.get(1));
        assertEquals(LIVE, statusList.get(2));
        assertEquals(4, getAssignmentRecordsCount().longValue());
        assertEquals(ACTOR_ID, getActorFromAssignmentTable());
        logger.info(" History record count after create request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count after create assignment request: {}", getAssignmentRecordsCount());
        logger.info(" LIVE table actor Id after create assignment request : {}", getActorFromAssignmentTable());

        //Insert role assignment records with replace existing is True
        AssignmentRequest assignmentRequestWithReplaceExistingTrue = TestDataBuilder.createRoleAssignmentRequest(
            true,
            true
        );

        assignmentRequestWithReplaceExistingTrue.getRequest().setAssignerId("6b36bfc6-bb21-11ea-b3de-0242ac130006");
        logger.info(
            "** Creating another role assignment record with request :   {}",
            mapper.writeValueAsString(assignmentRequestWithReplaceExistingTrue)
        );

        mockMvc.perform(post(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .content(mapper.writeValueAsBytes(assignmentRequestWithReplaceExistingTrue))
        ).andExpect(status().is(201)).andReturn();

        List<String> newStatusList = getStatusFromHistory();
        assertNotNull(newStatusList);
        assertEquals(8, newStatusList.size());
        assertEquals(CREATE_REQUESTED.toString(), newStatusList.get(0));
        assertEquals(APPROVED, newStatusList.get(1));
        assertEquals(LIVE, newStatusList.get(2));
        assertEquals(DELETE_APPROVED, newStatusList.get(3));
        assertEquals(CREATE_REQUESTED.toString(), newStatusList.get(4));
        assertEquals(APPROVED, newStatusList.get(5));
        assertEquals(DELETED, newStatusList.get(6));
        assertEquals(LIVE, newStatusList.get(7));
        assertEquals(4, getAssignmentRecordsCount().longValue());
        assertEquals(ACTOR_ID, getActorFromAssignmentTable());
        logger.info(" History record count after create request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count after create assignment request : {}", getAssignmentRecordsCount());
        logger.info(" LIVE table actor Id after create assignment request : {}", getActorFromAssignmentTable());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_assignment_records_to_delete.sql"})
    public void shouldDeleteRoleAssignmentsByProcessAndReference() throws Exception {

        logger.info(" Method shouldDeleteRoleAssignmentsByProcessAndReference starts :");
        logger.info(" History record count before create assignment request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count before create assignment request : {}", getAssignmentRecordsCount());
        final var url = "/am/role-assignments";

        mockMvc.perform(delete(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .param("process", "S-052")
                            .param("reference", "S-052")
        )
            .andExpect(status().is(204))
            .andReturn();

        assertAssignmentRecords();

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_assignment_records_to_delete.sql"})
    public void shouldDeleteRoleAssignmentsByAssignmentId() throws Exception {

        logger.info(" Method shouldDeleteRoleAssignmentsByAssignmentId starts : ");
        logger.info(" History record count before create assignment request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count before create assignment request : {}", getAssignmentRecordsCount());
        final var url = "/am/role-assignments/" + ASSIGNMENT_ID;

        mockMvc.perform(delete(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .param("process", "S-052")
                            .param("reference", "S-052")
        )
            .andExpect(status().is(204))
            .andReturn();

        assertAssignmentRecords();

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_assignment_records_to_delete.sql"})
    public void shouldDeleteSingleRoleAssignmentByAdvancedQuery() throws Exception {

        assertEquals(Integer.valueOf(2), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(3), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(1), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(1), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(1), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETED));

        mockMvc.perform(post(ADV_DELETE_URL)
                            .contentType(JSON_CONTENT_TYPE)
                            .content(createRoleAssignmentRequestAdvanceDelete())
                            .headers(getHttpHeaders())
        )
            .andExpect(status().is(HttpStatus.OK.value()))
            .andReturn();

        assertEquals(Integer.valueOf(1), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(5), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(1), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(1), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(1), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(1), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(1), getStatusCount(DELETED));

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_multiple_assignments_to_delete.sql"})
    public void shouldDeleteMultipleRoleAssignmentByAdvancedQuery() throws Exception {
        Case retrievedCase1 = Case.builder().id("1234567890123457")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        Case retrievedCase2 = Case.builder().id("1234567890123458")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        Case retrievedCase3 = Case.builder().id("1234567890123456")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        doReturn(retrievedCase1).when(retrieveDataService).getCaseById("1234567890123457");
        doReturn(retrievedCase2).when(retrieveDataService).getCaseById("1234567890123458");
        doReturn(retrievedCase3).when(retrieveDataService).getCaseById("1234567890123456");
        assertEquals(Integer.valueOf(4), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(9), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(3), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(3), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETED));

        mockMvc.perform(post(ADV_DELETE_URL)
                            .contentType(JSON_CONTENT_TYPE)
                            .content(createRoleAssignmentRequestAdvanceDeleteMultiple())
                            .headers(getHttpHeaders())
        )
            .andExpect(status().is(HttpStatus.OK.value()))
            .andReturn();

        assertEquals(Integer.valueOf(1), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(15), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(3), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(3), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(3), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(DELETED));

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_multiple_assignments_to_delete.sql"})
    public void shouldNotDeleteAllRoleAssignmentWhenQueryInvalid() throws Exception {
        Case retrievedCase1 = Case.builder().id("1234567890123457")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        Case retrievedCase2 = Case.builder().id("1234567890123458")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        Case retrievedCase3 = Case.builder().id("1234567890123456")
            .caseTypeId("Asylum")
            .jurisdiction("IA")
            .build();
        doReturn(retrievedCase1).when(retrieveDataService).getCaseById("1234567890123457");
        doReturn(retrievedCase2).when(retrieveDataService).getCaseById("1234567890123458");
        doReturn(retrievedCase3).when(retrieveDataService).getCaseById("1234567890123456");
        assertEquals(Integer.valueOf(4), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(9), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(3), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(3), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETED));

        mockMvc.perform(post(ADV_DELETE_URL)
                            .contentType(JSON_CONTENT_TYPE)
                            .content(
                                "{\"queryRequests\" : [{\"caseId\" : [ \"1234567890123456\" ]}]}"
                            )
                            .headers(getHttpHeaders())
        )
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertEquals(Integer.valueOf(4), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(9), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(3), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(3), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETED));

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_multiple_assignments_to_delete.sql"})
    public void shouldDeleteSomeRoleAssignmentsByAdvancedQuery() throws Exception {

        assertEquals(Integer.valueOf(4), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(9), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(3), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(3), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(0), getStatusCount(DELETED));

        mockMvc.perform(post(ADV_DELETE_URL)
                            .contentType(JSON_CONTENT_TYPE)
                            .content(createRoleAssignmentRequestAdvanceDelete())
                            .headers(getHttpHeaders())
        )
            .andExpect(status().is(HttpStatus.OK.value()))
            .andReturn();

        assertEquals(Integer.valueOf(3), getAssignmentRecordsCount());
        assertEquals(Integer.valueOf(11), getHistoryRecordsCount());

        assertEquals(Integer.valueOf(3), getStatusCount(CREATED));
        assertEquals(Integer.valueOf(3), getStatusCount(APPROVED));
        assertEquals(Integer.valueOf(3), getStatusCount(LIVE));
        assertEquals(Integer.valueOf(1), getStatusCount(DELETE_APPROVED));
        assertEquals(Integer.valueOf(1), getStatusCount(DELETED));

    }

    private String createRoleAssignmentRequestAdvanceDelete() {

        return "{\"queryRequests\":[{\"actorId\":[\"123e4567-e89b-42d3-a456-556642445612\"],"
            + "\"roleName\": [\"lead-judge\"],"
            + "\"roleType\": [\"CASE\"]}"
            + "]}";
    }

    private String createRoleAssignmentRequestAdvanceDeleteMultiple() {
        return "{\"queryRequests\" : [ "
            + "{"
            + "\"attributes\" : {"
            + "\"caseId\" : [ \"1234567890123456\" ]"
            + "}"
            + "},"
            + "{"
            + "\"attributes\" : {"
            + "\"caseId\" : [ \"1234567890123457\" ]"
            + "}"
            + "},"
            + "{"
            + "\"attributes\" : {"
            + "\"caseId\" : [ \"1234567890123458\" ]"
            + "}"
            + "}"
            + "]"
            + "}";
    }

    private void assertAssignmentRecords() {
        logger.info(" History record count after create assignment request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count after create assignment request : {}", getAssignmentRecordsCount());
        List<String> statusList = getStatusFromHistory();
        assertEquals(5, statusList.size());
        assertEquals(CREATED, statusList.get(0));
        assertEquals(APPROVED, statusList.get(1));
        assertEquals(LIVE, statusList.get(2));
        assertEquals(DELETE_APPROVED, statusList.get(3));
        assertEquals(DELETED, statusList.get(4));
    }

    private Integer getHistoryRecordsCount() {
        return template.queryForObject(COUNT_HISTORY_RECORDS_QUERY, Integer.class);
    }

    private Integer getAssignmentRecordsCount() {
        return template.queryForObject(COUNT_ASSIGNMENT_RECORDS_QUERY, Integer.class);
    }

    @NotNull
    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTHORIZATION, "Bearer user1");
        var s2SToken = MockUtils.generateDummyS2SToken(AUTHORISED_SERVICE);
        headers.add("ServiceAuthorization", "Bearer " + s2SToken);
        headers.add(Constants.CORRELATION_ID_HEADER_NAME, "38a90097-434e-47ee-8ea1-9ea2a267f51d");
        return headers;
    }

    public List<String> getStatusFromHistory() {
        return template.queryForList(GET_ASSIGNMENT_STATUS_QUERY, new Object[]{ACTOR_ID}, String.class);
    }

    public Integer getStatusCount(String status) {
        return template.queryForObject(GET_STATUS_COUNT_QUERY + "'" + status + "'", Integer.class);
    }

    public String getActorFromAssignmentTable() {
        return template.queryForObject(GET_ACTOR_FROM_ASSIGNMENT_QUERY, new Object[]{ACTOR_ID}, String.class);
    }
}
