package uk.gov.hmcts.reform.roleassignment.controller;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.roleassignment.BaseTest;

import java.nio.charset.StandardCharsets;

import static org.springdoc.core.Constants.SWAGGER_UI_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

public class SwaggerRedirectControllerIntegrationTest extends BaseTest {

    private transient MockMvc mockMvc;

    private static final MediaType JSON_CONTENT_TYPE = new MediaType(
        MediaType.APPLICATION_JSON.getType(),
        MediaType.APPLICATION_JSON.getSubtype(),
        StandardCharsets.UTF_8
    );

    @Autowired
    private transient SwaggerRedirectController swaggerRedirectController;

    @Before
    public void setUp() {
        this.mockMvc = standaloneSetup(this.swaggerRedirectController).build();
    }

    @Test
    public void swaggerRedirectApiTest() throws Exception {
        final var url = "/swagger";
        mockMvc.perform(get(url).contentType(JSON_CONTENT_TYPE))
            .andExpect(redirectedUrl(SWAGGER_UI_URL))
            .andReturn();
    }
}
