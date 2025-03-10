package uk.gov.hmcts.reform.roleassignment.util;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

class JacksonUtilsTest {

    @Test
    void convertValueJsonNode() {
        assertNotNull(JacksonUtils.convertValueJsonNode("NodeMe"));
    }

    @Test
    void getHashMapTypeReference() {
        assertNotNull(JacksonUtils.getHashMapTypeReference());
    }

    @Test
    void verifyStaticBlockFileNotFound() {
        MockedStatic<Files> classMock = Mockito.mockStatic(Files.class);
        classMock.when(() -> Files.walk(any(Path.class))).thenThrow(IOException.class);
        assertNotNull(JacksonUtils.getConfiguredRoles());
        classMock.close();
    }

    @Test
    void verifyStaticBlockStreamFailed() {
        MockedStatic<Files> classMock = Mockito.mockStatic(Files.class);
        classMock.when(() -> Files.newInputStream(any(Path.class))).thenThrow(IOException.class);
        assertNotNull(JacksonUtils.getConfiguredRoles());
        classMock.close();
    }
}
