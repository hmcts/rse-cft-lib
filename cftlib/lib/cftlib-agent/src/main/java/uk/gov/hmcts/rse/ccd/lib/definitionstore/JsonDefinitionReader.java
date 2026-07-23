package uk.gov.hmcts.rse.ccd.lib.definitionstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final String EXCLUDED_FILENAME_PATTERNS = "CCD_DEF_EXCLUDED_FILENAME_PATTERNS";

    private static final Map<String, List<String>> SHEET_PATHS = Map.of(
            "EventToComplexTypes", List.of("EventToComplexTypes", "CaseEventToComplexTypes"),
            "SearchCasesResultFields", List.of("SearchCasesResultFields", "SearchCaseResultFields")
    );

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
                var jsonDefinition = fromJson(path);
                var template = findTemplate(path);
                if (template != null) {
                    Map<String, DefinitionSheet> definition = super.parse(Files.newInputStream(template));
                    jsonDefinition.forEach((sheetName, sheet) -> {
                        if (!sheet.getDataItems().isEmpty() || !definition.containsKey(sheetName)) {
                            definition.put(sheetName, sheet);
                        }
                    });
                    return definition;
                }
                return jsonDefinition;
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
    public static List<Map<String, Object>> readPath(String path) {
        var fi = Paths.get(path).toFile();
        List<File> files = new ArrayList<>();
        if (fi.exists()) {
            try (var paths = Files.walk(fi.toPath())) {
                files = paths
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .sorted(JsonDefinitionReader::compareJsonFiles)
                        .collect(Collectors.toList());
            }
        }
        final var file = Paths.get(path + ".json").toFile();

        files.add(file);

        return files.stream()
                .filter(f -> f.exists() && f.getName().endsWith(".json") && f.canRead() && !isExcluded(f))
                .flatMap(JsonDefinitionReader::readFile)
                .collect(Collectors.toList());
    }

    private static int compareJsonFiles(File first, File second) {
        var firstName = first.getName().replaceFirst("\\.json$", "");
        var secondName = second.getName().replaceFirst("\\.json$", "");
        if (secondName.startsWith(firstName)) {
            return -1;
        }
        if (firstName.startsWith(secondName)) {
            return 1;
        }
        return first.getPath().compareTo(second.getPath());
    }

    private static boolean isExcluded(File file) {
        var configuredPatterns = System.getProperty(EXCLUDED_FILENAME_PATTERNS);
        if (configuredPatterns == null) {
            configuredPatterns = System.getenv(EXCLUDED_FILENAME_PATTERNS);
        }
        if (configuredPatterns == null) {
            configuredPatterns = "*-prod.json";
        }

        return Arrays.stream(configuredPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .anyMatch(matcher -> matcher.matches(file.toPath().getFileName()));
    }

    @SneakyThrows
    private static Stream<Map<String, Object>> readFile(File file) {
        var s = FileUtils.readFileToString(file);
        for (var entry : definitionEnvironmentVariables().entrySet()) {
            s = s.replace("${" + entry.getKey() + "}", entry.getValue());
        }

        List<Map<String, Object>> entries = mapper.readValue(
                s,
                new TypeReference<List<Map<String, Object>>>() { }
        );

        return entries.stream().flatMap(JsonDefinitionReader::expandAccessControl);
    }

    private static Map<String, String> definitionEnvironmentVariables() {
        var result = new LinkedHashMap<String, String>();
        addDefinitionEnvironmentVariables(result, System.getenv());

        Properties properties = System.getProperties();
        properties.stringPropertyNames().stream()
                .filter(JsonDefinitionReader::isDefinitionEnvironmentVariable)
                .forEach(key -> result.put(key, properties.getProperty(key)));
        return result;
    }

    private static void addDefinitionEnvironmentVariables(Map<String, String> result, Map<String, String> values) {
        values.entrySet().stream()
                .filter(entry -> isDefinitionEnvironmentVariable(entry.getKey()))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
    }

    private static boolean isDefinitionEnvironmentVariable(String name) {
        return name.startsWith("CCD_DEF") || name.startsWith("ET_COS") || name.startsWith("ET_ENV");
    }

    private static Stream<Map<String, Object>> expandAccessControl(Map<String, Object> row) {
        var accessControl = row.get("AccessControl");
        if (accessControl != null) {
            if (!(accessControl instanceof Collection<?> controls) || controls.isEmpty()) {
                throw new IllegalArgumentException("AccessControl must be a non-empty array");
            }
            if (row.containsKey("UserRole") || row.containsKey("CRUD")) {
                throw new IllegalArgumentException("AccessControl cannot be combined with UserRole or CRUD");
            }

            return controls.stream().flatMap(control -> {
                if (!(control instanceof Map<?, ?> values)) {
                    throw new IllegalArgumentException("AccessControl entries must be objects");
                }
                return expandUserRoles(row, values.get("UserRoles"), values.get("CRUD"), "AccessControl", true);
            });
        }

        var userRoles = row.get("UserRoles");
        if (userRoles != null) {
            if (row.containsKey("UserRole")) {
                throw new IllegalArgumentException("UserRoles cannot be combined with UserRole");
            }
            return expandUserRoles(row, userRoles, row.get("CRUD"), "UserRoles", false);
        }
        return Stream.of(row);
    }

    private static Stream<Map<String, Object>> expandUserRoles(
            Map<String, Object> source,
            Object userRoles,
            Object crud,
            String sourceField,
            boolean crudRequired
    ) {
        if (!(userRoles instanceof Collection<?> roles) || roles.isEmpty()) {
            throw new IllegalArgumentException(sourceField + " requires a non-empty UserRoles array");
        }
        if (crudRequired && (crud == null || crud.toString().isBlank())) {
            throw new IllegalArgumentException(sourceField + " requires a non-empty CRUD value");
        }

        return roles.stream().map(role -> {
            var expanded = new LinkedHashMap<>(source);
            expanded.remove("AccessControl");
            expanded.remove("UserRoles");
            expanded.put("UserRole", role);
            expanded.put("CRUD", crud);
            return expanded;
        });
    }

    @SneakyThrows
    public static Map<String, List<Map<String, Object>>> toJson(final String path) {
        var templateSheetPaths = templateSheetPaths(path);
        return FILES.stream()
                .map(file -> new AbstractMap.SimpleEntry<>(
                        file,
                        JsonDefinitionReader.readPath(resolveSheetPath(path, file, templateSheetPaths))
                ))
                .collect(Collectors.toMap(
                    AbstractMap.SimpleEntry::getKey,
                    AbstractMap.SimpleEntry::getValue,
                    (left, right) -> left,
                    LinkedHashMap::new
                ));
    }

    private static String resolveSheetPath(
            String root,
            String sheetName,
            Map<String, String> templateSheetPaths
    ) {
        var candidates = new ArrayList<>(SHEET_PATHS.getOrDefault(sheetName, List.of(sheetName)));
        var templatePath = templateSheetPaths.get(sheetName);
        if (templatePath != null && !candidates.contains(templatePath)) {
            candidates.add(templatePath);
        }
        return candidates.stream()
                .map(candidate -> pathFor(root, candidate))
                .filter(JsonDefinitionReader::pathExists)
                .findFirst()
                .orElseGet(() -> pathFor(root, candidates.get(0)));
    }

    @SneakyThrows
    private static Map<String, String> templateSheetPaths(String jsonPath) {
        var template = findTemplate(jsonPath);
        if (template == null) {
            return Map.of();
        }

        var result = new HashMap<String, String>();
        try (var input = Files.newInputStream(template); var workbook = new XSSFWorkbook(input)) {
            workbook.sheetIterator().forEachRemaining(sheet -> {
                var firstRow = sheet.getRow(0);
                if (firstRow != null && firstRow.getCell(0) != null) {
                    result.put(firstRow.getCell(0).getStringCellValue(), sheet.getSheetName());
                }
            });
        }
        return result;
    }

    @SneakyThrows
    private static Map<String, Set<String>> templateSheetHeaders(String jsonPath) {
        var template = findTemplate(jsonPath);
        if (template == null) {
            return Map.of();
        }

        var result = new HashMap<String, Set<String>>();
        try (var input = Files.newInputStream(template); var workbook = new XSSFWorkbook(input)) {
            workbook.sheetIterator().forEachRemaining(sheet -> {
                var firstRow = sheet.getRow(0);
                var headerRow = sheet.getRow(2);
                if (firstRow != null && firstRow.getCell(0) != null && headerRow != null) {
                    var headers = new LinkedHashSet<String>();
                    headerRow.cellIterator().forEachRemaining(cell -> {
                        var value = cell.getStringCellValue();
                        if (!value.isBlank()) {
                            headers.add(value);
                        }
                    });
                    result.put(firstRow.getCell(0).getStringCellValue(), headers);
                }
            });
        }
        return result;
    }

    private static Path findTemplate(String jsonPath) {
        var jsonDirectory = Paths.get(jsonPath).toAbsolutePath().normalize();
        var jurisdictionDirectory = jsonDirectory.getParent();
        if (jurisdictionDirectory == null) {
            return null;
        }
        var template = jurisdictionDirectory.resolve("data/ccd-template.xlsx");
        return Files.isRegularFile(template) ? template : null;
    }

    private static String pathFor(String root, String sheetName) {
        return Paths.get(root, sheetName).toString();
    }

    private static boolean pathExists(String path) {
        return Files.exists(Paths.get(path)) || Files.exists(Paths.get(path + ".json"));
    }

    public static Map<String, DefinitionSheet> fromJson(String path) {
        Map<String, DefinitionSheet> result = new HashMap<>();
        var j = toJson(path);
        var templateHeaders = templateSheetHeaders(path);
        final var dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (String s : j.keySet()) {
            var sheet = j.get(s);
            var defSheet = new DefinitionSheet();
            defSheet.setName(s);
            result.put(s, defSheet);
            for (Map<String, Object> row : sheet) {
                var item = new DefinitionDataItem(s);
                defSheet.getDataItems().add(item);
                for (String s1 : row.keySet()) {
                    if (templateHeaders.containsKey(s) && !templateHeaders.get(s).contains(s1)) {
                        continue;
                    }
                    Object val = row.get(s1);
                    // Match the behaviour of apache's spreadsheet parser
                    if (null != val) {
                        // Numbers are expected to be strings
                        if (val instanceof Number) {
                            val = val.toString();
                        }
                        if (val.toString().contains("\r")) {
                            val = val.toString().replace("\r", "");
                        }
                        if (val instanceof String stringValue && stringValue.isEmpty()) {
                            val = null;
                        }
                        if (val != null && (s1.equals("LiveFrom") || s1.equals("LiveTo"))) {
                            var ld = LocalDate.parse(val.toString(), dateFormatter);
                            val = Date.from(ld.atStartOfDay(ZoneId.of("UTC")).toInstant());
                        }
                    }
                    item.addAttribute(s1, val);
                }
            }
        }
        return result;
    }
}
