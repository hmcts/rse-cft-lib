package uk.gov.hmcts.reform.roleassignment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.BaseTest;
import uk.gov.hmcts.reform.roleassignment.MockUtils;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.util.Constants;
import uk.gov.hmcts.reform.roleassignment.versions.V2;

import javax.inject.Inject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"launchdarkly.sdk.environment=pr"})
public class QueryAssignmentIntegrationTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentIntegrationTest.class);

    private static final String URL = "/am/role-assignments/query";
    private static final String INCLUDE_LABELS_PARAM = "includeLabels";

    private static final String ACTOR_ID = "123e4567-e89b-42d3-a456-556642445613";
    private static final String ACTOR_ID_2 = "8bc0a13d-3bb7-3b7c-ab5b-1a9b0a141bab";

    private MockMvc mockMvc;

    @Inject
    private WebApplicationContext wac;

    @MockBean
    private IdamApi idamApi;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        MockitoAnnotations.openMocks(this);
        UserInfo userInfo = UserInfo.builder()
            .uid("6b36bfc6-bb21-11ea-b3de-0242ac130006")
            .sub("emailId@a.com")
            .build();
        doReturn(userInfo).when(idamApi).retrieveUserInfo(anyString());
        doReturn(authentication).when(securityContext).getAuthentication();
        SecurityContextHolder.setContext(securityContext);
        MockUtils.setSecurityAuthorities(authentication, MockUtils.ROLE_CASEWORKER);
    }

    @Test
    public void shouldGetIdLdDemo() throws Exception {

        logger.info("Launch Darkly flag check is successful for the endpoint");
        final var url = "/am/role-assignments/ld/endpoint";

        final MvcResult result = mockMvc.perform(get(url).contentType(JSON_CONTENT_TYPE))
            .andExpect(status().isOk())
            .andReturn();
        var responseAsString = result.getResponse().getContentAsString();
        assertEquals("Launch Darkly flag check is successful for the endpoint", responseAsString);
    }

    @Test
    public void retrieveRoleAssignmentsByQueryRequest_withoutBody() throws Exception {

        logger.info("Retrieve Role Assignments without Body");

        mockMvc.perform(post(URL)
             .contentType(JSON_CONTENT_TYPE))
             .andExpect(status().is(400))
             .andExpect(jsonPath("$.errorDescription")
                           .value("Input for Request body parameter is not valid"))
             .andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequest_PageSizeAndSort() throws Exception {

        logger.info("Retrieve Role Assignments verify return entries with size 2");

        final MvcResult result = mockMvc.perform(post(URL)
                                                 .contentType(JSON_CONTENT_TYPE)
                                                 .headers(getHttpHeaders("2", "roleCategory"))
                                                 .content(mapper.writeValueAsBytes(
                                                     QueryRequest.builder()
                                                         .actorId("123e4567-e89b-42d3-a456-556642445613").build())))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode responseJsonNode = new ObjectMapper().readValue(result.getResponse().getContentAsString(),
                                                           JsonNode.class);
        assertFalse(responseJsonNode.get("roleAssignmentResponse").isEmpty());
        assertEquals(2, responseJsonNode.get("roleAssignmentResponse").size());
        assertEquals("ORGANISATION", responseJsonNode.get("roleAssignmentResponse").get(0)
            .get("roleType").asText());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequest_queryRequestWithoutHeaders() throws Exception {

        logger.info("Retrieve Role Assignments with Query Request");

        QueryRequest queryRequest = createQueryRequest();
        mockMvc.perform(post(URL)
                                                     .contentType(JSON_CONTENT_TYPE)
                                                     .content(mapper.writeValueAsBytes(queryRequest)))
                                                     .andExpect(status().isOk())
            .andExpect(jsonPath("$.roleAssignmentResponse[0]").exists())
            .andExpect(jsonPath("$.roleAssignmentResponse[0].roleType")
                           .value(queryRequest.getRoleType().get(0)))
            .andExpect(jsonPath("$.roleAssignmentResponse[0].roleName")
                           .value(queryRequest.getRoleName().get(0)))
            .andExpect(jsonPath("$.roleAssignmentResponse[0].grantType")
                           .value(queryRequest.getGrantType().get(0)))
            .andExpect(jsonPath("$.roleAssignmentResponse[0].actorId")
                           .value(queryRequest.getActorId().get(0)))
            .andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequest_verifyRoleLabel() throws Exception {
        retrieveRoleAssignmentsByQueryRequest_conditionallyAddRoleLabel(true);
        retrieveRoleAssignmentsByQueryRequest_conditionallyAddRoleLabel(false);
    }

    void retrieveRoleAssignmentsByQueryRequest_conditionallyAddRoleLabel(Boolean includeLabels) throws Exception {

        logger.info("Retrieve Role Assignments when includeLabels {}", includeLabels);

        final MvcResult result = mockMvc.perform(post(URL)
                                                     .contentType(JSON_CONTENT_TYPE)
                                                     .headers(getHttpHeaders("3", "roleName"))
                                                     .param(INCLUDE_LABELS_PARAM, includeLabels.toString())
                                                     .content(mapper.writeValueAsBytes(
                                                         QueryRequest.builder()
                                                             .actorId(ACTOR_ID_2).build())))
            .andExpect(status().isOk())
            .andReturn();

        assertRoleAssignmentResponse(result, includeLabels);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequest_unmatchedQueryRequest() throws Exception {

        logger.info("Retrieve Role Assignments with unmatched Query Request");

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(List.of(ACTOR_ID))
            .roleCategory(List.of(RoleCategory.JUDICIAL.toString()))
            .validAt(now())
            .build();
        mockMvc.perform(post(URL)
                                                     .contentType(JSON_CONTENT_TYPE)
                                                     .content(mapper.writeValueAsBytes(queryRequest)))
                                                     .andExpect(status().isOk())
            .andExpect(jsonPath("$.roleAssignmentResponse").isEmpty())
            .andReturn();
    }

    @Test
    public void retrieveRoleAssignmentsByQueryRequestV2_withoutBody() throws Exception {

        logger.info("Retrieve Role Assignments without Body");

        mockMvc.perform(post(URL)
                            .contentType(V2.MediaType.POST_ASSIGNMENTS))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().is(400))
            .andExpect(jsonPath("$.errorDescription")
                           .value("Input for Request body parameter is not valid"))
            .andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_queryRequests() throws Exception {

        logger.info("Retrieve Role Assignments with two Query Requests in the list to verify return entries");
        QueryRequest queryRequest = createQueryRequest();
        QueryRequest queryRequest2 = QueryRequest.builder()
            .actorId(List.of(ACTOR_ID))
            .roleType(List.of(RoleType.CASE.toString())).build();
        MultipleQueryRequest queryRequests  =  MultipleQueryRequest.builder().queryRequests(
            List.of(queryRequest, queryRequest2)).build();

        mockMvc.perform(post("/am/role-assignments/query")
                        .contentType(V2.MediaType.POST_ASSIGNMENTS)
                        .headers(getHttpHeaders("20", "id"))
                        .content(mapper.writeValueAsString(queryRequests)))
            .andExpect(status().isOk())
            //.andDo(print())
            .andExpect(jsonPath("$.roleAssignmentResponse[0]").exists())
            .andExpect(jsonPath("$.roleAssignmentResponse[0].roleName")
                           .value(queryRequest.getRoleName().get(0)))
            .andExpect(jsonPath("$.roleAssignmentResponse[0].actorId")
                           .value(queryRequest.getActorId().get(0)))
            .andExpect(jsonPath("$.roleAssignmentResponse[0].grantType")
                           .value(queryRequest.getGrantType().get(0)))
            .andExpect(jsonPath("$.roleAssignmentResponse[0].roleType")
                           .value(queryRequest.getRoleType().get(0)))
            .andReturn();

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_PageSize() throws Exception {

        logger.info("Retrieve Role Assignments with Sort Request to verify 2 entries sort by "
                        + "roleCategory and NULL roleCategory should go end");
        QueryRequest queryRequest = QueryRequest.builder().actorId("123e4567-e89b-42d3-a456-556642445613").build();
        MultipleQueryRequest queryRequests  =  MultipleQueryRequest.builder().queryRequests(List.of(queryRequest))
            .build();

        final MvcResult result = mockMvc.perform(post("/am/role-assignments/query")
                                                     .contentType(V2.MediaType.POST_ASSIGNMENTS)
                                                     .headers(getHttpHeaders("2", "roleCategory"))
                                                     .content(mapper.writeValueAsString(queryRequests))
                                                     .accept(V2.MediaType.POST_ASSIGNMENTS))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode responseJsonNode = new ObjectMapper()
            .readValue(result.getResponse().getContentAsString(),JsonNode.class);
        assertFalse(responseJsonNode.get("roleAssignmentResponse").isEmpty());
        assertEquals(2, responseJsonNode.get("roleAssignmentResponse").size());
        assertEquals("ORGANISATION", responseJsonNode.get("roleAssignmentResponse").get(0)
            .get("roleType").asText());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_verifyRoleLabel() throws Exception {
        retrieveRoleAssignmentsByQueryRequestV2_conditionallyAddRoleLabel(true);
        retrieveRoleAssignmentsByQueryRequestV2_conditionallyAddRoleLabel(false);
    }

    void retrieveRoleAssignmentsByQueryRequestV2_conditionallyAddRoleLabel(Boolean includeLabels) throws Exception {

        logger.info("Retrieve Role Assignments V2 query request when includeLabels {}", includeLabels);

        QueryRequest queryRequest = QueryRequest.builder().actorId(ACTOR_ID_2).build();
        MultipleQueryRequest queryRequests  =  MultipleQueryRequest.builder().queryRequests(List.of(queryRequest))
            .build();

        final MvcResult result = mockMvc.perform(post("/am/role-assignments/query")
                                                     .contentType(V2.MediaType.POST_ASSIGNMENTS)
                                                     .headers(getHttpHeaders("3", "roleName"))
                                                     .param(INCLUDE_LABELS_PARAM, includeLabels.toString())
                                                     .content(mapper.writeValueAsString(queryRequests))
                                                     .accept(V2.MediaType.POST_ASSIGNMENTS))
            .andExpect(status().isOk())
            .andReturn();

        assertRoleAssignmentResponse(result, includeLabels);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_EmptyAttributeMappingDoesNotQueryEntireDb() throws Exception {

        logger.info("Retrieve Zero Role Assignments when Empty Key or Value Attribute Pairing");

        Map<String, List<String>> emptyKeyAttr = new HashMap<>();
        emptyKeyAttr.put("", Collections.singletonList("divorce"));
        QueryRequest queryRequest = QueryRequest.builder().attributes(emptyKeyAttr).build();

        Map<String, List<String>> emptyValueAttr = new HashMap<>();
        emptyValueAttr.put("jurisdiction", Collections.singletonList(""));
        QueryRequest queryRequest2 = QueryRequest.builder().attributes(emptyValueAttr).build();

        MultipleQueryRequest queryRequests  =
            MultipleQueryRequest.builder().queryRequests(List.of(queryRequest, queryRequest2)).build();

        final MvcResult result = mockMvc.perform(post("/am/role-assignments/query")
                                                     .contentType(V2.MediaType.POST_ASSIGNMENTS)
                                                     .headers(getHttpHeaders("2", "roleCategory"))
                                                     .content(mapper.writeValueAsString(queryRequests))
                                                     .accept(V2.MediaType.POST_ASSIGNMENTS))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode responseJsonNode = new ObjectMapper()
            .readValue(result.getResponse().getContentAsString(),JsonNode.class);
        assertEquals(0, responseJsonNode.get("roleAssignmentResponse").size());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_EmptyListPropertyDoesNotQueryEntireDb() throws Exception {

        logger.info("Retrieve Zero Role Assignments when Empty List Property Present");

        QueryRequest queryRequest = QueryRequest.builder().actorId(List.of("")).build();

        MultipleQueryRequest queryRequests  =
            MultipleQueryRequest.builder().queryRequests(List.of(queryRequest)).build();

        final MvcResult result = mockMvc.perform(post("/am/role-assignments/query")
                                                     .contentType(V2.MediaType.POST_ASSIGNMENTS)
                                                     .headers(getHttpHeaders("2", "roleCategory"))
                                                     .content(mapper.writeValueAsString(queryRequests))
                                                     .accept(V2.MediaType.POST_ASSIGNMENTS))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode responseJsonNode = new ObjectMapper()
            .readValue(result.getResponse().getContentAsString(),JsonNode.class);
        assertEquals(0, responseJsonNode.get("roleAssignmentResponse").size());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_PageSize_EmptyRequestDoesNotQueryEntireDb() throws Exception {

        logger.info("Retrieve Zero Role Assignments when Empty Request");

        QueryRequest queryRequest = QueryRequest.builder().build();

        MultipleQueryRequest queryRequests  =
            MultipleQueryRequest.builder().queryRequests(List.of(queryRequest)).build();

        mockMvc.perform(post("/am/role-assignments/query")
                                                     .contentType(V2.MediaType.POST_ASSIGNMENTS)
                                                     .headers(getHttpHeaders("2", "roleCategory"))
                                                     .content(mapper.writeValueAsString(queryRequests))
                                                     .accept(V2.MediaType.POST_ASSIGNMENTS))
            .andExpect(status().is4xxClientError())
            .andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_role_assignment.sql"})
    public void retrieveRoleAssignmentsByQueryRequestV2_PageSizeAndSortDesc() throws Exception {

        logger.info("Retrieve Role Assignments with Sort Request to verify 2 entries sort by "
                        + "roleCategory and NULL roleCategory should go end");
        QueryRequest queryRequest = QueryRequest.builder().actorId("123e4567-e89b-42d3-a456-556642445613").build();
        MultipleQueryRequest queryRequests  =  MultipleQueryRequest.builder().queryRequests(List.of(queryRequest))
            .build();
        HttpHeaders headers = getHttpHeaders("2", "roleCategory");
        headers.put("direction", Collections.singletonList("desc"));
        final MvcResult result = mockMvc.perform(post("/am/role-assignments/query")
                                                     .contentType(V2.MediaType.POST_ASSIGNMENTS)
                                                     .headers(headers)
                                                     .content(mapper.writeValueAsString(queryRequests))
                                                     .accept(V2.MediaType.POST_ASSIGNMENTS))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode responseJsonNode = new ObjectMapper()
            .readValue(result.getResponse().getContentAsString(),JsonNode.class);
        assertFalse(responseJsonNode.get("roleAssignmentResponse").isEmpty());
        assertEquals(2, responseJsonNode.get("roleAssignmentResponse").size());
        assertEquals("ORGANISATION", responseJsonNode.get("roleAssignmentResponse").get(0)
            .get("roleType").asText());
    }

    public static QueryRequest createQueryRequest() {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = List.of("London", "JAPAN", "north-east");
        List<String> contractTypes = List.of("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);

        return QueryRequest.builder()
            .actorId(List.of(ACTOR_ID))
            .roleType(List.of(RoleType.ORGANISATION.toString()))
            .roleName(List.of("judge"))
            .classification(List.of(Classification.PUBLIC.toString()))
            .grantType(List.of(GrantType.STANDARD.toString()))
            .validAt(now())
            .attributes(attributes)
            .build();
    }

    @NotNull
    private HttpHeaders getHttpHeaders(String size, String sort) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("pageNumber", "0");
        headers.add("size", size);
        headers.add("sort", sort);
        headers.add("direction", "asc");
        headers.add(Constants.CORRELATION_ID_HEADER_NAME, "38a90097-434e-47ee-8ea1-9ea2a267f51d");

        return headers;
    }

    private void assertRoleAssignmentResponse(MvcResult result, Boolean includeLabels) throws Exception {
        JsonNode responseJsonNode = new ObjectMapper().readValue(result.getResponse().getContentAsString(),
                                                                 JsonNode.class);
        JsonNode roleAssignmentResponse = responseJsonNode.get("roleAssignmentResponse");

        String[] expectedRoleTypes = {"CASE", "ORGANISATION", "CASE"};
        String[] expectedRoleLabels = {"Case Allocator", "Judge", "Post Hearing Judge"};

        assertFalse(roleAssignmentResponse.isEmpty());
        assertEquals(3, roleAssignmentResponse.size());

        for (int i = 0; i < roleAssignmentResponse.size(); i++) {
            assertEquals(expectedRoleTypes[i], roleAssignmentResponse.get(i).get("roleType").asText());
            if (includeLabels) {
                assertEquals(expectedRoleLabels[i], roleAssignmentResponse.get(i).get("roleLabel").asText());
            } else {
                assertNull(roleAssignmentResponse.get(i).get("roleLabel"));
            }
        }
    }

}
