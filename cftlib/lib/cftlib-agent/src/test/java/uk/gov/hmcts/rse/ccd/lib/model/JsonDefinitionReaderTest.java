package uk.gov.hmcts.rse.ccd.lib.model;

import com.google.common.collect.Sets;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.exception.InvalidImportException;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.exception.MapperException;
import uk.gov.hmcts.ccd.definition.store.excel.parser.SpreadsheetParser;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionDataItem;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionSheet;
import uk.gov.hmcts.ccd.definition.store.excel.util.mapper.ColumnName;
import uk.gov.hmcts.ccd.definition.store.excel.validation.SpreadsheetValidator;
import uk.gov.hmcts.rse.ccd.lib.definitionstore.JsonDefinitionReader;
import uk.gov.hmcts.rse.ccd.lib.JsonDefinitionImport;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonDefinitionReaderTest {

    @TempDir
    Path tempDir;

    @Test
    public void readsFileAndDirectory() {
        var result = JsonDefinitionReader.readPath("src/test/resources/definition/AuthorisationCaseType");

        assertEquals("caseworker-caa", result.get(0).get("UserRole"));
        assertEquals("caseworker-divorce-bulkscan", result.get(1).get("UserRole"));
        assertEquals("caseworker-divorce-courtadmin-la", result.get(2).get("UserRole"));
        assertEquals("caseworker-divorce-courtadmin_beta", result.get(3).get("UserRole"));
        assertEquals("caseworker-divorce-solicitor", result.get(4).get("UserRole"));
    }

    @SneakyThrows
    Map<String, DefinitionSheet> loadReferenceXlsx() {
        var parser = new SpreadsheetParser(new SpreadsheetValidator());
        var i = getClass().getClassLoader().getResourceAsStream("ccd-definition.xlsx");
        return parser.parse(i);
    }

    Map<String, DefinitionSheet> loadJsonDefinition() {
        return JsonDefinitionReader.fromJson("src/test/resources/definition");
    }

    @Test
    @SneakyThrows
    public void testParsesAllSheets() {
        var expected = loadReferenceXlsx();
        var actual = loadJsonDefinition();

        // Check that we have all the expected sheets
        assertThat(Sets.difference(expected.keySet(), actual.keySet())).isEmpty();

        // Compare each sheet
        for (String s1 : expected.keySet()) {
            var e = expected.get(s1);
            var a = actual.get(s1);
            assertSheetsEqual(e, a);
        }
    }

    @Test
    public void testSortsComplexTypesCorrectly() {
        // Ordered maps are returned.
        var expected = loadReferenceXlsx().get("ComplexTypes").groupDataItemsById();
        var actual = loadJsonDefinition().get("ComplexTypes").groupDataItemsById();
        assertThat(new ArrayList<>(actual.keySet())).isEqualTo(new ArrayList<>(expected.keySet()));
    }

    @Test
    @SneakyThrows
    void supportsDefinitionProcessorSheetNames() {
        Files.createDirectories(tempDir.resolve("EventToComplexTypes"));
        Files.writeString(
                tempDir.resolve("EventToComplexTypes/EventToComplexTypes.json"),
                """
                [{"ID":"event","FieldDisplayOrder":1}]
                """
        );
        Files.writeString(
                tempDir.resolve("SearchCaseResultFields.json"),
                """
                [{"CaseTypeID":"case-type","CaseFieldID":"field"}]
                """
        );

        var result = JsonDefinitionReader.toJson(tempDir.toString());

        assertThat(result.get("EventToComplexTypes")).hasSize(1);
        assertThat(result.get("SearchCasesResultFields")).hasSize(1);
    }

    @Test
    @SneakyThrows
    void usesTemplateSheetNamesForJsonDirectories() {
        var jsonDirectory = Files.createDirectories(tempDir.resolve("json"));
        var dataDirectory = Files.createDirectories(tempDir.resolve("data"));
        Files.createDirectories(jsonDirectory.resolve("Custom Lists"));
        Files.writeString(
                jsonDirectory.resolve("Custom Lists/Custom Lists.json"),
                """
                [{"ID":"fixed-list","ListElementCode":"value","ListElement":"Value","DisplayOrder":1}]
                """
        );

        try (var workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(dataDirectory.resolve("ccd-template.xlsx"))) {
            var sheet = workbook.createSheet("Custom Lists");
            sheet.createRow(0).createCell(0).setCellValue("FixedLists");
            workbook.write(output);
        }

        var result = JsonDefinitionReader.toJson(new JsonDefinitionImport(jsonDirectory.toString(),
                dataDirectory.resolve("ccd-template.xlsx").toString(), Map.of(), List.of()));

        assertThat(result.get("FixedLists")).hasSize(1);
        assertThat(result.get("FixedLists").get(0).get("ID")).isEqualTo("fixed-list");
    }

    @Test
    @SneakyThrows
    void ignoresJsonPropertiesThatAreNotTemplateColumns() {
        var jsonDirectory = Files.createDirectories(tempDir.resolve("json"));
        var dataDirectory = Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(
                jsonDirectory.resolve("CaseEventToFields.json"),
                """
                [{"CaseFieldID":"field","retainHiddenValue":"Yes"}]
                """
        );

        try (var workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(dataDirectory.resolve("ccd-template.xlsx"))) {
            var sheet = workbook.createSheet("CaseEventToFields");
            sheet.createRow(0).createCell(0).setCellValue("CaseEventToFields");
            var headers = sheet.createRow(2);
            headers.createCell(0).setCellValue("CaseFieldID");
            headers.createCell(1).setCellValue("RetainHiddenValue");
            workbook.write(output);
        }

        var item = JsonDefinitionReader.fromJson(new JsonDefinitionImport(jsonDirectory.toString(),
                dataDirectory.resolve("ccd-template.xlsx").toString(), Map.of(), List.of()))
                .get("CaseEventToFields")
                .getDataItems()
                .get(0);

        assertThat(item.getCaseFieldId()).isEqualTo("field");
        assertThat(item.findAttribute(ColumnName.RETAIN_HIDDEN_VALUE)).isNull();
    }

    @Test
    @SneakyThrows
    void onlyExcludesExplicitFilenamePatterns() {
        Files.createDirectories(tempDir.resolve("CaseEvent"));
        Files.writeString(
                tempDir.resolve("CaseEvent/CaseEvent.json"),
                """
                [{"ID":"base"}]
                """
        );
        Files.writeString(
                tempDir.resolve("CaseEvent/CaseEvent-prod.json"),
                """
                [{"ID":"prod"}]
                """
        );
        Files.writeString(
                tempDir.resolve("CaseEvent/CaseEvent-nonprod.json"),
                """
                [{"ID":"nonprod"}]
                """
        );

        var result = JsonDefinitionReader.readPath(tempDir.resolve("CaseEvent").toString());

        assertThat(result).extracting(row -> row.get("ID")).containsExactly("base", "nonprod", "prod");

        var filtered = JsonDefinitionReader.toJson(new JsonDefinitionImport(tempDir.toString(), null,
                Map.of(), List.of("*-prod.json"))).get("CaseEvent");
        assertThat(filtered).extracting(row -> row.get("ID")).containsExactly("base", "nonprod");
    }

    @Test
    @SneakyThrows
    void expandsDefinitionProcessorAccessControlShorthand() {
        Files.writeString(
                tempDir.resolve("AuthorisationCaseEvent.json"),
                """
                [{
                  "CaseTypeID":"case-type",
                  "CaseEventID":"event",
                  "AccessControl":[
                    {"UserRoles":["role-a","role-b"],"CRUD":"CRUD"},
                    {"UserRoles":["role-c"],"CRUD":"R"}
                  ]
                }]
                """
        );

        var result = JsonDefinitionReader.toJson(tempDir.toString()).get("AuthorisationCaseEvent");

        assertThat(result).extracting(row -> row.get("UserRole"))
                .containsExactly("role-a", "role-b", "role-c");
        assertThat(result).extracting(row -> row.get("CRUD"))
                .containsExactly("CRUD", "CRUD", "R");
        assertThat(result).allSatisfy(row -> assertThat(row).doesNotContainKey("AccessControl"));
    }

    @Test
    @SneakyThrows
    void expandsUserRolesWithoutRequiringCrud() {
        Files.writeString(
                tempDir.resolve("AuthorisationCaseType.json"),
                """
                [{"CaseTypeID":"case-type","UserRoles":["role-a","role-b"]}]
                """
        );

        var result = JsonDefinitionReader.toJson(tempDir.toString()).get("AuthorisationCaseType");

        assertThat(result).extracting(row -> row.get("UserRole")).containsExactly("role-a", "role-b");
    }

    @Test
    @SneakyThrows
    void mapsExpandedUserRolesToAccessProfileTemplateColumns() {
        var template = tempDir.resolve("ccd-template.xlsx");
        Files.writeString(
                tempDir.resolve("AuthorisationCaseType.json"),
                """
                [{"CaseTypeID":"case-type","UserRoles":["role-a"],"CRUD":"CRUD"}]
                """
        );
        try (var workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(template)) {
            var sheet = workbook.createSheet("AuthorisationCaseType");
            sheet.createRow(0).createCell(0).setCellValue("AuthorisationCaseType");
            var headers = sheet.createRow(2);
            headers.createCell(0).setCellValue("CaseTypeID");
            headers.createCell(1).setCellValue("AccessProfile");
            headers.createCell(2).setCellValue("CRUD");
            workbook.write(output);
        }

        var item = JsonDefinitionReader.fromJson(new JsonDefinitionImport(
                tempDir.toString(), template.toString(), Map.of(), List.of()
        )).get("AuthorisationCaseType").getDataItems().get(0);

        assertThat(item.findAttribute(ColumnName.ACCESS_PROFILE)).isEqualTo("role-a");
    }

    @Test
    @SneakyThrows
    void importsConfiguredJsonPayloadWithTemplateSubstitutionsAndExclusions() {
        var jsonDirectory = Files.createDirectories(tempDir.resolve("json"));
        var template = tempDir.resolve("ccd-template.xlsx");
        Files.writeString(jsonDirectory.resolve("CaseType.json"),
                """
                [{"ID":"case-type","Name":"${CASE_NAME}","PrintableDocumentsUrl":"${SERVICE_URL}/documents"}]
                """);
        Files.writeString(jsonDirectory.resolve("CaseType-prod.json"),
                """
                [{"ID":"production-case-type","Name":"Production"}]
                """);
        try (var workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(template)) {
            var sheet = workbook.createSheet("CaseType");
            sheet.createRow(0).createCell(0).setCellValue("CaseType");
            var headers = sheet.createRow(2);
            headers.createCell(0).setCellValue("ID");
            headers.createCell(1).setCellValue("Name");
            headers.createCell(2).setCellValue("PrintableDocumentsUrl");
            workbook.write(output);
        }
        var request = new JsonDefinitionImport(
                jsonDirectory.toString(),
                template.toString(),
                Map.of("CASE_NAME", "Example case", "SERVICE_URL", "http://localhost:8081"),
                List.of("*-prod.json")
        );
        var payload = JsonDefinitionImport.PREFIX + new ObjectMapper().writeValueAsString(request);
        var reader = new JsonDefinitionReader(new SpreadsheetValidator());

        var result = reader.parse(new ByteArrayInputStream(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var items = result.get("CaseType").getDataItems();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).findAttribute(ColumnName.NAME)).isEqualTo("Example case");
        assertThat(items.get(0).findAttribute(ColumnName.PRINTABLE_DOCUMENTS_URL))
                .isEqualTo("http://localhost:8081/documents");
    }

    @Test
    @SneakyThrows
    void preservesTemplateRowsAfterBlankWorksheetPositions() {
        var jsonDirectory = Files.createDirectories(tempDir.resolve("json"));
        var template = tempDir.resolve("ccd-template.xlsx");
        Files.writeString(jsonDirectory.resolve("CaseType.json"),
                "[{\"ID\":\"json-case-type\",\"Name\":\"JSON case type\"}]");
        try (var workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(template)) {
            var sheet = workbook.createSheet("CaseType");
            sheet.createRow(0).createCell(0).setCellValue("CaseType");
            var headers = sheet.createRow(2);
            headers.createCell(0).setCellValue("ID");
            headers.createCell(1).setCellValue("Name");
            sheet.createRow(3);
            var trailingDefault = sheet.createRow(4);
            trailingDefault.createCell(0).setCellValue("trailing-default");
            trailingDefault.createCell(1).setCellValue("Trailing default");
            workbook.write(output);
        }
        var request = new JsonDefinitionImport(
                jsonDirectory.toString(), template.toString(), Map.of(), List.of()
        );
        var payload = JsonDefinitionImport.PREFIX + new ObjectMapper().writeValueAsString(request);
        var reader = new JsonDefinitionReader(new SpreadsheetValidator());

        var items = reader.parse(new ByteArrayInputStream(
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )).get("CaseType").getDataItems();

        assertThat(items).hasSize(2);
        assertThat(items).extracting(item -> item.findAttribute(ColumnName.NAME))
                .containsExactly("JSON case type", "Trailing default");
    }

    @Test
    @SneakyThrows
    void doesNotDiscoverEnvironmentFilesOrTemplates() {
        Files.createDirectories(tempDir.resolve("configs/environment"));
        Files.writeString(tempDir.resolve("configs/environment/env.json"), "not a configuration file");
        Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(tempDir.resolve("data/ccd-template.xlsx"), "not a workbook");
        var jsonDirectory = Files.createDirectories(tempDir.resolve("json"));
        Files.writeString(jsonDirectory.resolve("CaseType.json"), "[{\"ID\":\"case-type\"}]");

        assertThat(JsonDefinitionReader.toJson(jsonDirectory.toString()).get("CaseType"))
                .containsExactly(Map.of("ID", "case-type"));
    }

    @Test
    @SneakyThrows
    void explicitImportsDoNotReadProcessSubstitutions() {
        Files.writeString(tempDir.resolve("CaseType.json"),
                "[{\"ID\":\"case-type\",\"Name\":\"${CCD_DEF_IMPORT_TEST}\"}]");
        System.setProperty("CCD_DEF_IMPORT_TEST", "implicit");
        try {
            assertThrows(IllegalArgumentException.class, () -> JsonDefinitionReader.toJson(
                new JsonDefinitionImport(tempDir.toString(), null, Map.of(), List.of())));
            assertThat(JsonDefinitionReader.toJson(tempDir.toString()).get("CaseType").get(0).get("Name"))
                .isEqualTo("implicit");
        } finally {
            System.clearProperty("CCD_DEF_IMPORT_TEST");
        }
    }

    @Test
    @SneakyThrows
    void rejectsUnresolvedCustomSubstitutions() {
        Files.writeString(tempDir.resolve("CaseType.json"),
                "[{\"ID\":\"case-type\",\"Name\":\"${CASE_NAME}\"}]");
        var request = new JsonDefinitionImport(tempDir.toString(), null, Map.of(), List.of());

        assertThat(assertThrows(IllegalArgumentException.class, () -> JsonDefinitionReader.toJson(request)))
                .hasMessageContaining("${CASE_NAME}");
    }

    @Test
    @SneakyThrows
    void onlyImportsSheetsPresentInConfiguredTemplate() {
        var jsonDirectory = Files.createDirectories(tempDir.resolve("json"));
        var template = tempDir.resolve("ccd-template.xlsx");
        Files.writeString(jsonDirectory.resolve("CaseType.json"), "[{\"ID\":\"case-type\"}]");
        Files.writeString(jsonDirectory.resolve("FixedLists.json"),
                "[{\"ID\":\"list\",\"ListElementCode\":\"item\"}]");
        try (var workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(template)) {
            var sheet = workbook.createSheet("FixedLists");
            sheet.createRow(0).createCell(0).setCellValue("FixedLists");
            var headers = sheet.createRow(2);
            headers.createCell(0).setCellValue("ID");
            headers.createCell(1).setCellValue("ListElementCode");
            workbook.write(output);
        }
        var request = new JsonDefinitionImport(
                jsonDirectory.toString(), template.toString(), Map.of(), List.of()
        );
        var payload = JsonDefinitionImport.PREFIX + new ObjectMapper().writeValueAsString(request);
        var reader = new JsonDefinitionReader(new SpreadsheetValidator());

        var result = reader.parse(new ByteArrayInputStream(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(result).containsOnlyKeys("FixedLists");
        assertThat(result.get("FixedLists").getDataItems()).hasSize(1);
    }

    @Test
    @SneakyThrows
    void rejectsUnresolvedDefinitionEnvironmentVariables() {
        Files.writeString(
                tempDir.resolve("CaseType.json"),
                """
                [{"ID":"case-type","PrintableDocumentsUrl":"${CCD_DEF_MISSING}/documents"}]
                """
        );

        assertThrows(
                IllegalArgumentException.class,
            () -> JsonDefinitionReader.readPath(tempDir.resolve("CaseType").toString())
        );
    }

    @Test
    @SneakyThrows
    void sortsDuplicateFragmentNamesByPath() {
        var sheetDirectory = Files.createDirectories(tempDir.resolve("CaseEvent"));
        var firstDirectory = Files.createDirectories(sheetDirectory.resolve("a"));
        var secondDirectory = Files.createDirectories(sheetDirectory.resolve("b"));
        Files.writeString(firstDirectory.resolve("fragment.json"), "[{\"ID\":\"first\"}]");
        Files.writeString(secondDirectory.resolve("fragment.json"), "[{\"ID\":\"second\"}]");

        var result = JsonDefinitionReader.readPath(sheetDirectory.toString());

        assertThat(result).extracting(row -> row.get("ID")).containsExactly("first", "second");
    }

    @Test
    @SneakyThrows
    void sortsNestedFragmentsWithOneTotalPathOrdering() {
        var sheetDirectory = Files.createDirectories(tempDir.resolve("CaseEvent"));
        var firstDirectory = Files.createDirectories(sheetDirectory.resolve("a"));
        var secondDirectory = Files.createDirectories(sheetDirectory.resolve("b"));
        Files.writeString(firstDirectory.resolve("AB.json"), "[{\"ID\":\"first\"}]");
        Files.writeString(firstDirectory.resolve("B.json"), "[{\"ID\":\"second\"}]");
        Files.writeString(secondDirectory.resolve("A.json"), "[{\"ID\":\"third\"}]");

        var result = JsonDefinitionReader.readPath(sheetDirectory.toString());

        assertThat(result).extracting(row -> row.get("ID")).containsExactly("first", "second", "third");
    }

    @Test
    @SneakyThrows
    void validatesJsonCellsLikeSpreadsheetCells() {
        Files.writeString(
                tempDir.resolve("Jurisdiction.json"),
                """
                [{"ID":"EMPLOYMENT","Name":"This jurisdiction name is more than thirty characters long"}]
                """
        );
        var reader = new JsonDefinitionReader(new SpreadsheetValidator());

        assertThrows(
                InvalidImportException.class,
            () -> reader.parse(new ByteArrayInputStream(tempDir.toString().getBytes()))
        );
    }

    @Test
    @SneakyThrows
    void treatsEmptyJsonDatesAsSpreadsheetBlanks() {
        Files.writeString(
                tempDir.resolve("CaseType.json"),
                """
                [{"ID":"case-type","LiveFrom":"01/01/2017","LiveTo":""}]
                """
        );

        var item = JsonDefinitionReader.fromJson(tempDir.toString())
                .get("CaseType")
                .getDataItems()
                .get(0);

        assertThat(item.findAttribute(ColumnName.LIVE_FROM)).isNotNull();
        assertThat(item.findAttribute(ColumnName.LIVE_TO)).isNull();
    }

    /**
     * Compare two definition sheets for equality.
     * We sort the rows in each sheet deterministically and then compare row by row.
     */
    private void assertSheetsEqual(DefinitionSheet expected, DefinitionSheet actual) {
        assertThat(expected.getName()).isEqualTo(actual.getName());
        assertThat(actual.getDataItems().size()).isEqualTo(expected.getDataItems().size());
        Map<String, List<DefinitionDataItem>> unmatchedItems = new HashMap<>();
        actual.getDataItems().forEach(item ->
                unmatchedItems.computeIfAbsent(matchKey(item), key -> new ArrayList<>()).add(item)
        );
        for (DefinitionDataItem expectedItem : expected.getDataItems()) {
            var candidates = unmatchedItems.getOrDefault(matchKey(expectedItem), List.of());
            var matchingItem = candidates.stream()
                    .filter(actualItem -> itemsEqual(actualItem, expectedItem))
                    .findFirst();
            assertThat(matchingItem)
                    .as("No matching row in %s for %s", expected.getName(), expectedItem.getCaseFieldId())
                    .isPresent();
            candidates.remove(matchingItem.orElseThrow());
        }
        System.out.println(expected.getName() + " is ok");
    }

    private String matchKey(DefinitionDataItem item) {
        try {
            return item.getCaseFieldId();
        } catch (MapperException mapperException) {
            return "";
        }
    }

    private boolean itemsEqual(DefinitionDataItem actual, DefinitionDataItem expected) {
        try {
            assertItemsEqual(actual, expected);
            return true;
        } catch (AssertionFailedError assertionFailedError) {
            return false;
        }
    }

    @SneakyThrows
    private void assertItemsEqual(DefinitionDataItem actual, DefinitionDataItem expected) {
        for (ColumnName columnName : ColumnName.values()) {
            Object expectedValue;
            try {
                expectedValue = expected.findAttribute(columnName);
            } catch (MapperException ignored) {
                continue;
            }
            if (expectedValue == null) {
                continue;
            }
            try {
                assertThat(actual.findAttribute(columnName)).isEqualTo(expectedValue);
            } catch (AssertionFailedError a) {
                // TODO definition processor bug can convert dates to this number
                if (!"42736".equals(expectedValue)) {
                    throw a;
                }
            }
        }
    }
}
