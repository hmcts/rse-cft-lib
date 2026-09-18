package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CFTLibApiImplTest {

    @Test
    void failsWhenAnyDefinitionCannotBeDumped(@TempDir Path outputDir) {
        var cftLib = new CFTLibApiImpl() {
            @Override
            List<String> getCaseTypeReferences() {
                return List.of("valid-case-type", "invalid-case-type");
            }

            @Override
            String getCaseTypeDefinitionFromDefinitionStore(String caseTypeId) {
                if ("invalid-case-type".equals(caseTypeId)) {
                    throw new RuntimeException("Definition store rejected the request");
                }
                return "{\"id\":\"" + caseTypeId + "\"}";
            }
        };

        assertThatThrownBy(() -> cftLib.dumpDefinitionSnapshots(outputDir.toFile()))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to dump definition for invalid-case-type")
            .hasCauseInstanceOf(RuntimeException.class);

        assertThat(outputDir.resolve("valid-case-type.json")).hasContent(
            "{\"id\":\"valid-case-type\"}"
        );
        assertThat(outputDir.resolve("invalid-case-type.json")).doesNotExist();
    }
}
