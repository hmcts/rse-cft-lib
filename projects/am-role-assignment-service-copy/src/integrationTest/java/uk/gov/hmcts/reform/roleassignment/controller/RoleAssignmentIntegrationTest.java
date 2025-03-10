package uk.gov.hmcts.reform.roleassignment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.BaseTest;
import uk.gov.hmcts.reform.roleassignment.MockUtils;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.service.security.IdamRoleService;
import uk.gov.hmcts.reform.roleassignment.launchdarkly.FeatureConditionEvaluation;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.createQueryRequest;

public class RoleAssignmentIntegrationTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentIntegrationTest.class);
    private static final String COUNT_HISTORY_RECORDS_QUERY = "SELECT count(1) as n FROM role_assignment_history";

    private static final String GET_ASSIGNMENT_STATUS_QUERY = "SELECT actor_id FROM role_assignment where id = ?";
    private static final String ACTOR_ID = "123e4567-e89b-42d3-a456-556642445612";
    private static final String CASE_ID = "1234567890123456";
    private static final String ROLE_ASSIGNMENT_ID = "2ef8ebf3-266e-45d3-a3b8-4ce1e5d93b9f";
    private static final String ROLE_TYPE_CASE = RoleType.CASE.toString();
    private static final String ROLE_TYPE_ORG = RoleType.ORGANISATION.toString();

    private static final String URL_GET_ROLES = "/am/role-assignments/roles";
    private static final String URL_GET_ROLE_ASSIGNMENTS_FOR_ACTOR = "/am/role-assignments/actors/";
    private static final String URL_QUERY_ROLE_ASSIGNMENTS = "/am/role-assignments/query";

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
    private IdamRoleService idamRoleService;

    @MockBean
    private FeatureConditionEvaluation featureConditionEvaluation;

    @Before
    public void setUp() throws Exception {
        template = new JdbcTemplate(ds);
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        MockitoAnnotations.openMocks(this);
        UserInfo userInfo = UserInfo.builder()
            .uid(ACTOR_ID)
            .sub("emailId@a.com")
            .build();
        doReturn(userInfo).when(idamApi).retrieveUserInfo(anyString());
        doReturn(authentication).when(securityContext).getAuthentication();
        SecurityContextHolder.setContext(securityContext);
        MockUtils.setSecurityAuthorities(authentication, MockUtils.ROLE_CASEWORKER);
        doReturn(true).when(featureConditionEvaluation).preHandle(any(),any(),any());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {
        "classpath:sql/insert_role_assignment_request.sql",
        "classpath:sql/insert_role_assignment_history.sql"
    })
    public void shouldGetRecordCountFromHistoryTable() {
        final int count = template.queryForObject(COUNT_HISTORY_RECORDS_QUERY, Integer.class);
        logger.info(" Total number of records fetched from role assignment history table...{}", count);
        assertEquals(
            "role_assignment_history record count ", 15, count);
    }

    @Test
    public void disableTestAsPerFlagValue() {
        assertRoleAssignmentRecordSize();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void shouldGetRecordsFromRoleAssignmentTable() {
        assertRoleAssignmentRecordSize();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/insert_role_assignment.sql",
            "classpath:sql/insert_actor_cache_control.sql"})
    public void shouldGetRoleAssignmentsBasedOnActorId() throws Exception {
        assertRoleAssignmentRecordSize();
        final var url = URL_GET_ROLE_ASSIGNMENTS_FOR_ACTOR + ACTOR_ID;

        final MvcResult result = mockMvc.perform(get(url)
                                                     .contentType(JSON_CONTENT_TYPE)
                                                     .headers(getHttpHeaders()))
            .andExpect(status().is(200))
            .andReturn();

        List<ExistingRoleAssignment> existingRoleAssignments = getExistingRoleAssignmentFromMvcResult(result);

        assertNotNull(existingRoleAssignments);
        assertEquals(1, existingRoleAssignments.size());
        assertEquals(
            ROLE_ASSIGNMENT_ID,
            existingRoleAssignments.get(0).getId().toString()
        );
        assertEquals(
            ACTOR_ID,
            existingRoleAssignments.get(0).getActorId()
        );
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void shouldGetRoleAssignmentsBasedOnRoleTypeAndActorId() throws Exception {
        assertRoleAssignmentRecordSize();

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(Collections.singletonList(ACTOR_ID))
            .roleType(Collections.singletonList(ROLE_TYPE_CASE))
            .build();

        final MvcResult result = mockMvc.perform(post(URL_QUERY_ROLE_ASSIGNMENTS)
                                                     .contentType(MediaType.APPLICATION_JSON)
                                                     .headers(getHttpHeaders())
                                                     .content(mapper.writeValueAsBytes(queryRequest))
        )
            .andExpect(status().is(200))
            .andReturn();

        List<ExistingRoleAssignment> existingRoleAssignments = getExistingRoleAssignmentFromMvcResult(result);

        assertNotNull(existingRoleAssignments);
        assertEquals(1, existingRoleAssignments.size());
        assertEquals(
            ROLE_ASSIGNMENT_ID,
            existingRoleAssignments.get(0).getId().toString()
        );
        assertEquals(
            ACTOR_ID,
            existingRoleAssignments.get(0).getActorId()
        );
        assertEquals(
            ROLE_TYPE_CASE,
            existingRoleAssignments.get(0).getRoleType().toString()
        );
    }


    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void shouldGetRoleAssignmentsBasedOnRoleTypeAndCaseId() throws Exception {
        assertRoleAssignmentRecordSize();

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("caseId", Collections.singletonList(CASE_ID));

        QueryRequest queryRequest = QueryRequest.builder()
            .roleType(Collections.singletonList(ROLE_TYPE_CASE))
            .attributes(attributes)
            .build();

        final MvcResult result = mockMvc.perform(post(URL_QUERY_ROLE_ASSIGNMENTS)
                                                     .contentType(MediaType.APPLICATION_JSON)
                                                     .headers(getHttpHeaders())
                                                     .content(mapper.writeValueAsBytes(queryRequest))
        )
            .andExpect(status().is(200))
            .andReturn();

        List<ExistingRoleAssignment> existingRoleAssignments = getExistingRoleAssignmentFromMvcResult(result);

        assertNotNull(existingRoleAssignments);
        assertEquals(1, existingRoleAssignments.size());
        assertEquals(
            ROLE_ASSIGNMENT_ID,
            existingRoleAssignments.get(0).getId().toString()
        );
        assertEquals(
            ACTOR_ID,
            existingRoleAssignments.get(0).getActorId()
        );
        assertEquals(
            ROLE_TYPE_CASE,
            existingRoleAssignments.get(0).getRoleType().toString()
        );
        assertEquals(
            CASE_ID,
            existingRoleAssignments.get(0).getAttributes().get("caseId").asText()
        );
    }

    @Test
    public void shouldGetListOfRoles() throws Exception {

        final MvcResult result = mockMvc.perform(get(URL_GET_ROLES)
                                                     .contentType(MediaType.APPLICATION_JSON)
                                                     .headers(getHttpHeaders())
        )
            .andExpect(status().is(200))
            .andReturn();
        var response = result.getResponse().getContentAsString();

        List<RoleConfigRole> roleConfigRoles = mapper.readValue(response, new TypeReference<>() {
        });

        assertEquals(194, roleConfigRoles.size());
        for (RoleConfigRole roleConfigRole : roleConfigRoles) {
            assertNotNull(roleConfigRole.getName());
            assertNotNull(roleConfigRole.getCategory());
            assertNotNull(roleConfigRole.getType());
            assertNotNull(roleConfigRole.getLabel());
        }
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void shouldGetRoleAssignmentsRecordsBasedOnDynamicQuery() throws Exception {

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("jurisdiction", Collections.singletonList("divorce"));

        QueryRequest queryRequest = QueryRequest.builder()
            .roleType(Collections.singletonList(ROLE_TYPE_ORG))
            .roleName(Collections.singletonList("judge"))
            .actorId(Collections.singletonList("123e4567-e89b-42d3-a456-556642445613"))
            .attributes(attributes)
            .build();

        final MvcResult result = mockMvc.perform(post(URL_QUERY_ROLE_ASSIGNMENTS)
                                                     .contentType(JSON_CONTENT_TYPE)
                                                     .headers(getHttpHeaders())
                                                     .content(mapper.writeValueAsBytes(queryRequest))
        ).andExpect(status().is(200)).andReturn();

        List<ExistingRoleAssignment> existingRoleAssignments = getExistingRoleAssignmentFromMvcResult(result);

        assertNotNull(existingRoleAssignments);
        assertEquals(3, existingRoleAssignments.size());
        existingRoleAssignments.forEach(element -> assertAll(
            () -> assertEquals(ROLE_TYPE_ORG, element.getRoleType().toString()),
            () -> assertEquals("judge", element.getRoleName()),
            () -> assertEquals("123e4567-e89b-42d3-a456-556642445613", element.getActorId()),
            () -> assertEquals("STANDARD", element.getGrantType().toString()),
            () -> assertEquals("PUBLIC", element.getClassification().toString())
        ));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void shouldGetEmptyRoleAssignmentsRecordsBasedOnDynamicQuery() throws Exception {

        final MvcResult result = mockMvc.perform(post(URL_QUERY_ROLE_ASSIGNMENTS)
                                                     .contentType(JSON_CONTENT_TYPE)
                                                     .headers(getHttpHeaders())
                                                     .content(mapper.writeValueAsBytes(createQueryRequest()))
        ).andExpect(status().is(200)).andReturn();

        List<ExistingRoleAssignment> existingRoleAssignments = getExistingRoleAssignmentFromMvcResult(result);

        assertNotNull(existingRoleAssignments);
        assertEquals(0, existingRoleAssignments.size());

    }

    private void assertRoleAssignmentRecordSize() {
        final Object[] assignmentId = new Object[]{
            ROLE_ASSIGNMENT_ID
        };
        var actorId = template.queryForObject(GET_ASSIGNMENT_STATUS_QUERY, assignmentId, String.class);
        logger.info(" Role assignment actor id is...{}", actorId);
        assertEquals(
            "Role assignment actor Id", ACTOR_ID, actorId);
    }

    private List<ExistingRoleAssignment> getExistingRoleAssignmentFromMvcResult(MvcResult result)
        throws UnsupportedEncodingException, JsonProcessingException {
        JsonNode jsonResponse = mapper.readValue(result.getResponse().getContentAsString(), JsonNode.class);
        assertNotNull(jsonResponse.get("roleAssignmentResponse"));
        return mapper.readValue(
            jsonResponse.get("roleAssignmentResponse").toString(),
            new TypeReference<>() {
            }
        );
    }

    @NotNull
    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", "Bearer " + "1234");
        headers.add("Authorization", "Bearer " + "2345");
        return headers;
    }


}
