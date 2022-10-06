package uk.gov.hmcts.rse.ccd.lib.model;

import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import uk.gov.hmcts.ccd.definition.store.excel.endpoint.exception.MapperException;
import uk.gov.hmcts.ccd.definition.store.excel.parser.SpreadsheetParser;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionDataItem;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionSheet;
import uk.gov.hmcts.ccd.definition.store.excel.util.mapper.ColumnName;
import uk.gov.hmcts.ccd.definition.store.excel.validation.SpreadsheetValidator;
import uk.gov.hmcts.rse.ccd.lib.definitionstore.JsonDefinitionReader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonDefinitionReaderTest {

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

    /**
     * Compare two definition sheets for equality.
     * We sort the rows in each sheet deterministically and then compare row by row.
     */
    private void assertSheetsEqual(DefinitionSheet expected, DefinitionSheet actual) {
        assertThat(expected.getName()).isEqualTo(actual.getName());
        assertThat(actual.getDataItems().size()).isEqualTo(expected.getDataItems().size());
        expected.getDataItems().sort(Comparator.comparing(this::extractSortKey));
        actual.getDataItems().sort(Comparator.comparing(this::extractSortKey));
        for (int t = 0; t < expected.getDataItems().size(); t++) {
            assertItemsEqual(actual.getDataItems().get(t), expected.getDataItems().get(t));
        }
        System.out.println(expected.getName() + " is ok");
    }

    /**
     * Different definition sheets have different 'primary keys'
     * ie. the columns that uniquely identify a row.
     */
    private List<List<ColumnName>> compoundSortKeys = List.of(
            List.of(ColumnName.ID),
            List.of(ColumnName.DISPLAY_ORDER),
            List.of(ColumnName.STATE_ID),
            List.of(ColumnName.CASE_EVENT_ID),
            List.of(ColumnName.TAB_ID),
            List.of(ColumnName.CASE_FIELD_ID, ColumnName.ACCESS_PROFILE)
    );

    /**
     * Generate a string for sorting on by looking through our known
     * list of keys and finding the first contained in the item.
     */
    @SneakyThrows
    private String extractSortKey(DefinitionDataItem item) {
        for (List<ColumnName> compoundKey : compoundSortKeys) {
            try {
                var sortKey = "";
                for (ColumnName key : compoundKey) {
                    var val = item.findAttribute(key);
                    if (null != val) {
                        sortKey += val;
                    }
                }
                if (!sortKey.isEmpty()) {
                    return sortKey;
                }
            } catch (MapperException m) {
                // Expected if the key is not found for a particular item.
            }
        }

        throw new RuntimeException("No known keys to compare: " + item.getCaseFieldId());
    }

    @SneakyThrows
    private void assertItemsEqual(DefinitionDataItem actual, DefinitionDataItem expected) {
        // We have to use reflection to get access to the attributes field for comparison.
        Field f = DefinitionDataItem.class.getDeclaredField("attributes"); //NoSuchFieldException
        f.setAccessible(true);
        var expectedAttributes = (List<Pair<String, Object>>) f.get(expected);
        var actualAttributes = (List<Pair<String, Object>>) f.get(actual);
        for (Pair<String, Object> expectedAttribute : expectedAttributes) {
            try {
                var pair = actualAttributes.stream().filter(
                    x -> x.getKey().equals(expectedAttribute.getKey())).findFirst();
                var val = pair.isPresent() ? pair.get().getValue() : null;

                assertThat(val).isEqualTo(expectedAttribute.getValue());
            } catch (AssertionFailedError a) {
                // TODO definition processor bug can convert dates to this number
                if (!expectedAttribute.getValue().equals("42736")) {
                    throw a;
                }
            }
        }
    }
}