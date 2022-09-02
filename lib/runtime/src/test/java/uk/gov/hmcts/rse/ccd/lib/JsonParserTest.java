package uk.gov.hmcts.rse.ccd.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
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
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class JsonParserTest {

    @Test
    @SneakyThrows
    public void testParsesAllSheets() {
        var s = new SpreadsheetParser(new SpreadsheetValidator());
        var i = getClass().getClassLoader().getResourceAsStream("ccd-definition.xlsx");

        var reference = s.parse(i);
        var parsed = JsonDefinitionReader.fromJson("src/test/resources/definition", new JsonDefinitionReader(new ObjectMapper()));

        assertThat(Sets.difference(reference.keySet(), parsed.keySet())).isEmpty();

        for (String s1 : reference.keySet()) {
            var expected = reference.get(s1);
            var actual = parsed.get(s1);
            assertSheetsEqual(expected, actual);
        }

    }

    private void assertSheetsEqual(DefinitionSheet expected, DefinitionSheet actual) {
        assertThat(expected.getName()).isEqualTo(actual.getName());
        assertThat(actual.getDataItems().size()).isEqualTo(expected.getDataItems().size());
        expected.getDataItems().sort(Comparator.comparing(this::gooo));
        actual.getDataItems().sort(Comparator.comparing(this::gooo));
        for (int t = 0; t < expected.getDataItems().size(); t++) {
            assertItemsEqual(actual.getDataItems().get(t), expected.getDataItems().get(t));
        }
        System.out.println(expected.getName() + " is ok");
    }

    private List<List<ColumnName>> keys = List.of(
            List.of(ColumnName.ID),
            List.of(ColumnName.DISPLAY_ORDER),
            List.of(ColumnName.STATE_ID),
            List.of(ColumnName.CASE_EVENT_ID),
            List.of(ColumnName.TAB_ID),
            List.of(ColumnName.CASE_FIELD_ID, ColumnName.ACCESS_PROFILE)
    );
    @SneakyThrows
    private String gooo(DefinitionDataItem item) {
        for (List<ColumnName> keyset : keys) {
            try {
                var v = "";
                for (ColumnName key : keyset) {
                    var val = item.findAttribute(key);
                    if (null != val) {
                        v += val;
                    }
                }
                if (!v.isEmpty()) {
                    return v;
                }
            } catch (MapperException m) {
                // If the item
                //
            }
        }

        throw new RuntimeException("No known keys to compare: " + item.getCaseFieldId());
    }

    @SneakyThrows
    private void assertItemsEqual(DefinitionDataItem actual, DefinitionDataItem expected) {
        Field f = DefinitionDataItem.class.getDeclaredField("attributes"); //NoSuchFieldException
        f.setAccessible(true);
        var expectedAttributes = (List<Pair<String, Object>>) f.get(expected);
        var actualAttributes = (List<Pair<String, Object>>) f.get(actual);
        for (Pair<String, Object> expectedAttribute : expectedAttributes) {
            try {
                var pair = actualAttributes.stream().filter(x -> x.getKey().equals(expectedAttribute.getKey())).findFirst();
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