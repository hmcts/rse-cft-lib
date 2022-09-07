package uk.gov.hmcts.rse.ccd.lib.definitionstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.excel.parser.SpreadsheetParser;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionDataItem;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionSheet;
import uk.gov.hmcts.ccd.definition.store.excel.util.mapper.SheetName;
import uk.gov.hmcts.ccd.definition.store.excel.validation.SpreadsheetValidator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * Adds json definition parsing to CCD definition store.
 * Runs only in definition store.
 */
@ConditionalOnClass(CaseDataAPIApplication.class)
@Component
public class JsonDefinitionReader extends SpreadsheetParser {

    private static final List<String> FILES = Arrays.stream(SheetName.values())
            .map(SheetName::getName)
            .collect(Collectors.toList());

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public JsonDefinitionReader(SpreadsheetValidator spreadsheetValidator) {
        super(spreadsheetValidator);
    }

    /**
     * Extend CCD's spreadsheet parser with one that supports JSON.
     * Definition store expects an xlsx which we extend by supporting
     * the passing of a file path indicating the directory containing definition json.
     */
    @Override
    public Map<String, DefinitionSheet> parse(InputStream inputStream) throws IOException {
        var data = inputStream.readAllBytes();
        var path =  new String(data);
        try {
            if (Path.of(path).toFile().exists()) {
                return fromJson(path);
            }
        } catch (InvalidPathException i) {
            // Treat it as xlsx
        }
        return super.parse(new ByteArrayInputStream(data));
    }

    /**
     * Avoid an NPE since the list in the base class is initialised by the parse routine.
     */
    @Override
    public List<String> getImportWarnings() {
        return List.of();
    }

    @SneakyThrows
    public static List<Map<String, String>> readPath(String path) {
        var fi = Paths.get(path).toFile();
        List<File> files = new ArrayList<>();
        if (fi.exists()) {
            files = Files.walk(fi.toPath())
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
        final var file = Paths.get(path + ".json").toFile();

        files.add(file);

        return files.parallelStream()
                .filter(f -> f.exists() && f.getName().endsWith(".json") && f.canRead())
                .flatMap(JsonDefinitionReader::readFile)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private static Stream<Map<String, String>> readFile(File file) {
        List<Map<String, String>> entries = asList(mapper.readValue(file, Map[].class));

        return entries.stream();
    }

    @SneakyThrows
    public static Map<String, List<Map<String, String>>> toJson(final String path) {
        return FILES.parallelStream()
                .map(file -> {
                    var p = file;
                    // quick hack since this sheet's name doesn't follow convention.
                    if (file.equals("EventToComplexTypes")) {
                        p = "CaseEventToComplexTypes";
                    }
                    return new AbstractMap.SimpleEntry<>(file, JsonDefinitionReader.readPath(path + "/" + p));
                })
                .collect(Collectors.toUnmodifiableMap(AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue));
    }

    public static Map<String, DefinitionSheet> fromJson(String path) {
        Map<String, DefinitionSheet> result = new HashMap<>();
        var j = toJson(path);
        final var dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (String s : j.keySet()) {
            var sheet = j.get(s);
            var defSheet = new DefinitionSheet();
            defSheet.setName(s);
            result.put(s, defSheet);
            for (Map<String, String> row : sheet) {
                var item = new DefinitionDataItem(s);
                defSheet.getDataItems().add(item);
                for (String s1 : row.keySet()) {
                    Object val = row.get(s1);
                    // Match the behaviour of apache's spreadsheet parser
                    if (null != val) {
                        // Numbers are expected to be strings
                        if (val.getClass().equals(Integer.class)) {
                            val = val.toString();
                        }
                        if (s1.equals("LiveFrom") || s1.equals("LiveTo")) {
                            var ld = LocalDate.parse(val.toString(), dateFormatter);
                            val = Date.from(ld.atStartOfDay(ZoneId.of("UTC")).toInstant());
                        }
                        if (val.toString().contains("\r")) {
                            val = val.toString().replace("\r", "");
                        }
                        if (val.getClass().equals(String.class)) {
                            if (((String) val).isEmpty()) {
                                val = null;
                            }
                        }
                    }
                    item.addAttribute(s1, val);
                }
            }
        }
        return result;
    }
}
