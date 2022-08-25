package uk.gov.hmcts.rse.ccd.lib.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.ResourceUtils;
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;
import uk.gov.hmcts.rse.ccd.lib.repository.CaseTypeRepository;
import uk.gov.hmcts.rse.ccd.lib.repository.FieldTypeRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.jsonpath.JsonPathAdapter.inPath;
import static net.javacrumbs.jsonunit.spring.JsonUnitResultMatchers.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = {
        CaseDefinitionController.class,
        DisplayApiController.class
})
class CaseDefinitionControllerTest {

    @Autowired
    CaseDefinitionController controller;

    @TestConfiguration
    static class InnerConfiguration {
        private final Map<String, String> paths = Map.of(
            "NFD", "src/test/resources/definition"
        );

        @Bean
        public JsonDefinitionReader getReader() {
            return new JsonDefinitionReader(new ObjectMapper());
        }

        @Bean
        public FieldTypeRepository getFieldTypeRepository() {
            return new FieldTypeRepository();
        }

        @Bean
        public CaseTypeRepository getRepository() {
            return new CaseTypeRepository(paths, getReader(), getFieldTypeRepository());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dataCaseTypeIdGet() throws Exception {
        var expected = resourceAsString("classpath:response/case-definition/case-type.json");
        var matcher = json().when(IGNORING_ARRAY_ORDER);
        var matchers = new ArrayList<ResultMatcher>();

        matchers.add(matcher.node("id").isEqualTo(inPath(expected, "$.id")));
        matchers.add(matcher.node("description").isEqualTo(inPath(expected, "$.description")));
        matchers.add(matcher.node("version").isEqualTo(inPath(expected, "$.version")));
        matchers.add(matcher.node("jurisdiction").isEqualTo(inPath(expected, "$.jurisdiction")));
        matchers.add(matcher.node("name").isEqualTo(inPath(expected, "$.name")));
        matchers.add(matcher.node("acls").isEqualTo(inPath(expected, "$.acls")));
        matchers.add(matcher.node("security_classification").isEqualTo(inPath(expected, "$.security_classification")));
        matchers.add(matcher.node("states").isEqualTo(inPath(expected, "$.states")));

        for (int i = 0; i < 593; i++) {
            matchers.add(matcher.node("case_fields[" + i + "]").isEqualTo(inPath(expected, "$.case_fields[" + i + "]")));
        }

        for (int i = 0; i < 126; i++) {
            matchers.add(matcher.node("events[" + i + "]").isEqualTo(inPath(expected, "$.events[" + i + "]")));
        }

        mockMvc
                .perform(get("/api/data/case-type/NFD"))
                .andExpectAll(matchers.toArray(new ResultMatcher[0]));
    }

    @SneakyThrows
    @Test
    public void testTabs() {
        var expected = resourceAsString("classpath:response/display/tab-structure.json");
        var actual = mockMvc
                .perform(get("/api/display/tab-structure/NFD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

//        var mapper = new ObjectMapper();
//        var json = mapper.readValue(actual, Object.class);
//        var pretty = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(json);
//        Files.writeString(Paths.get("mine.json"), pretty);
        assertThatJson(actual)
                .when(IGNORING_ARRAY_ORDER)
                .isEqualTo(expected);
    }

    @SneakyThrows
    @Test
    public void testWorkbasketResults() {
        var expected = resourceAsString("classpath:response/display/workbasket.json");
        var actual = mockMvc
                .perform(get("/api/display/work-basket-definition/NFD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThatJson(actual)
                .when(IGNORING_ARRAY_ORDER)
                .isEqualTo(expected);
    }

    @SneakyThrows
    @Test
    public void testWorkbasketInputs() {
        var expected = resourceAsString("classpath:response/display/workbasket-input.json");
        var actual = mockMvc
                .perform(get("/api/display/work-basket-input-definition/NFD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThatJson(actual)
                .when(IGNORING_ARRAY_ORDER)
                .isEqualTo(expected);
    }

    @SneakyThrows
    @Test
    public void testSearchInputs() {
        var expected = resourceAsString("classpath:response/display/search-input.json");
        var actual = mockMvc
                .perform(get("/api/display/search-input-definition/NFD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThatJson(actual)
                .when(IGNORING_ARRAY_ORDER)
                .isEqualTo(expected);
    }

    @SneakyThrows
    @Test
    public void testSearchResults() {
        var expected = resourceAsString("classpath:response/display/search-result.json");
        var actual = mockMvc
                .perform(get("/api/display/search-result-definition/NFD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThatJson(actual)
                .when(IGNORING_ARRAY_ORDER)
                .isEqualTo(expected);
    }

    @SneakyThrows
    @Test
    public void testSearchCasesResults() {
        var expected = resourceAsString("classpath:response/display/search-cases-result-fields.json");
        var actual = mockMvc
                .perform(get("/api/display/search-cases-result-fields/NFD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThatJson(actual)
                .when(IGNORING_ARRAY_ORDER)
                .isEqualTo(expected);
    }

    @Test
    void dataCaseworkerIdAndJurisdictionIdCaseTypeGet() throws Exception {
        var expected = resourceAsString("classpath:response/case-definition/case-type.json");
        var matcher = json().when(IGNORING_ARRAY_ORDER);
        var matchers = new ArrayList<ResultMatcher>();

        matchers.add(matcher.node("id").isEqualTo(inPath(expected, "$.id")));
        matchers.add(matcher.node("states").isEqualTo(inPath(expected, "$.states")));

        mockMvc
            .perform(get("/api/data/caseworkers/ignored/jurisdictions/ignored/case-types/NFD"))
            .andExpectAll(matchers.toArray(new ResultMatcher[0]));
    }

    @Test
    void getRoleToAccessProfiles() throws Exception {
        var expected = resourceAsString("classpath:response/case-definition/case-assignments.json");

        mockMvc
            .perform(get("/api/data/caseworkers/ignored/jurisdictions/ignored/case-types/NFD/access/profile/roles"))
            .andExpect(json().isEqualTo(expected));
    }

    @Test
    void dataCaseTypeVersionGet() throws Exception {
        var expected = resourceAsString("classpath:response/case-definition/case-type-version.json");

        mockMvc
            .perform(get("/api/data/case-type/NFD/version"))
            .andExpect(json().isEqualTo(expected));
    }

    private static String resourceAsString(final String resourcePath) throws IOException {
        final File file = ResourceUtils.getFile(resourcePath);
        return new String(Files.readAllBytes(file.toPath()));
    }

}