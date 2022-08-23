package uk.gov.hmcts.rse.ccd.lib.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.jsonpath.JsonPathAdapter.inPath;
import static net.javacrumbs.jsonunit.spring.JsonUnitResultMatchers.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CaseDefinitionController.class)
class CaseDefinitionControllerTest {

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
        var expected = resourceAsString("classpath:case-type.json");
        var matcher = json().when(IGNORING_ARRAY_ORDER);
        var matchers = new ArrayList<ResultMatcher>();
        matchers.add(matcher.node("id").isEqualTo(inPath(expected, "$.id")));
        matchers.add(matcher.node("states").isEqualTo(inPath(expected, "$.states")));

        for (int i = 0; i < 90; i++) {
            matchers.add(matcher.node("case_fields[" + i + "]").isEqualTo(inPath(expected, "$.case_fields[" + i + "]")));
        }

//        matchers.add(matcher.node("events[0]").isEqualTo(inPath(expected, "$.events[0]")));
//        matchers.add(matcher.isEqualTo(expected));

        mockMvc
            .perform(get("/api/data/case-type/NFD"))
            .andExpectAll(matchers.toArray(new ResultMatcher[0]));
    }

    private static String resourceAsString(final String resourcePath) throws IOException {
        final File file = ResourceUtils.getFile(resourcePath);
        return new String(Files.readAllBytes(file.toPath()));
    }

}