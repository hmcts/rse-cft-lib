package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;

import com.google.gson.JsonParser;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CFTLibApiImplTest {

    @Test
    void sendsExplicitJsonImportOptionsWithoutChangingSourceFiles(@TempDir Path directory) throws IOException {
        var definitions = Files.createDirectories(directory.resolve("définitions"));
        final var source = Files.writeString(definitions.resolve("CaseType.json"), "[{\"ID\":\"${CASE_TYPE}\"}]");
        var template = Files.writeString(directory.resolve("template.xlsx"), "template");
        var uploads = new ArrayList<String>();
        var lib = new CFTLibApiImpl() {
            @Override
            void postDefinition(byte[] data) {
                uploads.add(new String(data, StandardCharsets.UTF_8));
            }
        };

        lib.importJsonDefinition(definitions.toFile(), template.toFile(),
            Map.of("CASE_TYPE", "example"), "*-skip.json");
        lib.importJsonDefinition(definitions.toFile());

        assertThat(uploads).hasSize(2);
        assertThat(uploads.get(0)).startsWith(JsonDefinitionImport.PREFIX);
        var payload = JsonParser.parseString(uploads.get(0).substring(JsonDefinitionImport.PREFIX.length()))
            .getAsJsonObject();
        assertThat(payload.get("directory").getAsString()).isEqualTo(definitions.toFile().getCanonicalPath());
        assertThat(payload.get("template").getAsString()).isEqualTo(template.toFile().getCanonicalPath());
        assertThat(payload.getAsJsonObject("substitutions").get("CASE_TYPE").getAsString()).isEqualTo("example");
        assertThat(payload.getAsJsonArray("excludedFilenamePatterns").get(0).getAsString()).isEqualTo("*-skip.json");
        assertThat(uploads.get(1)).isEqualTo(definitions.toFile().getCanonicalPath());
        assertThat(source).hasContent("[{\"ID\":\"${CASE_TYPE}\"}]");
    }

    @Test
    void rejectsMissingJsonDirectoryOrExplicitTemplate(@TempDir Path directory) {
        var lib = new CFTLibApiImpl();
        assertThatThrownBy(() -> lib.importJsonDefinition(directory.resolve("missing").toFile()))
            .isInstanceOf(FileNotFoundException.class);
        assertThatThrownBy(() -> lib.importJsonDefinition(directory.toFile(),
            directory.resolve("missing.xlsx").toFile(), Map.of()))
            .isInstanceOf(FileNotFoundException.class);
    }

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
