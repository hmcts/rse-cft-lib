package uk.gov.hmcts.rse.ccd.lib.definitionstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.rse.ccd.lib.JsonDefinitionImport;
import uk.gov.hmcts.ccd.definition.store.excel.parser.SpreadsheetParser;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionDataItem;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionSheet;
import uk.gov.hmcts.ccd.definition.store.excel.util.mapper.SheetName;
import uk.gov.hmcts.ccd.definition.store.excel.validation.SpreadsheetValidator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Pattern;
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

    private static final Pattern UNRESOLVED_SUBSTITUTION = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*}");

    private final SpreadsheetValidator spreadsheetValidator;

    private static final Map<String, List<String>> SHEET_PATHS = Map.of(
            "EventToComplexTypes", List.of("EventToComplexTypes", "CaseEventToComplexTypes"),
            "SearchCasesResultFields", List.of("SearchCasesResultFields", "SearchCaseResultFields")
    );

    @Autowired
    public JsonDefinitionReader(SpreadsheetValidator spreadsheetValidator) {
        super(spreadsheetValidator);
        this.spreadsheetValidator = spreadsheetValidator;
    }

    /**
     * Extend CCD's spreadsheet parser with one that supports JSON.
     * Definition store expects an xlsx which we extend by supporting
     * the passing of a file path indicating the directory containing definition json.
     */
    @Override
    public Map<String, DefinitionSheet> parse(InputStream inputStream) throws IOException {
        var data = inputStream.readAllBytes();
        var path = new String(data, StandardCharsets.UTF_8);
        if (path.startsWith(JsonDefinitionImport.PREFIX)) {
            var request = mapper.readValue(
                    path.substring(JsonDefinitionImport.PREFIX.length()),
                    JsonDefinitionImport.class
            );
            return parseJson(request);
        }
        try {
            if (Files.isDirectory(Path.of(path))) {
                return parseJson(defaultImport(path));
            }
        } catch (InvalidPathException i) {
            // Treat it as xlsx
        }
        return super.parse(new ByteArrayInputStream(data));
    }

    private Map<String, DefinitionSheet> parseJson(JsonDefinitionImport request) throws IOException {
        var jsonDefinition = fromJson(request, spreadsheetValidator);
        if (request.template() == null) {
            return jsonDefinition;
        }
        var templateRowOffsets = templateDataRowOffsets(request.template());
        try (var input = Files.newInputStream(Path.of(request.template()))) {
            Map<String, DefinitionSheet> definition = super.parse(input);
            jsonDefinition.forEach((sheetName, sheet) -> mergeJsonRows(
                    definition,
                    sheetName,
                    sheet,
                    templateRowOffsets.getOrDefault(sheetName, List.of())
            ));
            return definition;
        }
    }

    private static void mergeJsonRows(
            Map<String, DefinitionSheet> definition,
            String sheetName,
            DefinitionSheet jsonSheet,
            List<Integer> templateRowOffsets
    ) {
        var templateSheet = definition.get(sheetName);
        if (templateSheet == null) {
            definition.put(sheetName, jsonSheet);
            return;
        }

        var templateRows = templateSheet.getDataItems();
        var jsonRows = jsonSheet.getDataItems();
        var mergedRows = new ArrayList<>(jsonRows);
        for (var rowIndex = 0; rowIndex < templateRows.size(); rowIndex++) {
            var worksheetOffset = rowIndex < templateRowOffsets.size()
                    ? templateRowOffsets.get(rowIndex)
                    : rowIndex;
            if (worksheetOffset >= jsonRows.size()) {
                mergedRows.add(templateRows.get(rowIndex));
            }
        }
        templateRows.clear();
        templateRows.addAll(mergedRows);
    }

    @SneakyThrows
    private static Map<String, List<Integer>> templateDataRowOffsets(String template) {
        var result = new HashMap<String, List<Integer>>();
        try (var input = Files.newInputStream(Path.of(template)); var workbook = new XSSFWorkbook(input)) {
            workbook.sheetIterator().forEachRemaining(sheet -> {
                var firstRow = sheet.getRow(0);
                if (firstRow == null || firstRow.getCell(0) == null) {
                    return;
                }
                var rowOffsets = new ArrayList<Integer>();
                for (var rowIndex = 3; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    var row = sheet.getRow(rowIndex);
                    if (row != null && row.cellIterator().hasNext() && rowHasValues(row)) {
                        rowOffsets.add(rowIndex - 3);
                    }
                }
                result.put(firstRow.getCell(0).getStringCellValue(), rowOffsets);
            });
        }
        return result;
    }

    private static boolean rowHasValues(org.apache.poi.ss.usermodel.Row row) {
        for (var cell : row) {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                if (!cell.getStringCellValue().isBlank()) {
                    return true;
                }
            } else if (cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK) {
                return true;
            }
        }
        return false;
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
        return readPath(path, defaultImport(path));
    }

    @SneakyThrows
    private static List<Map<String, Object>> readPath(String path, JsonDefinitionImport request) {
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
                .filter(f -> f.exists()
                        && f.getName().endsWith(".json")
                        && f.canRead()
                        && !isExcluded(f, request.excludedFilenamePatterns()))
                .flatMap(fileToRead -> readFile(fileToRead, request.substitutions()))
                .collect(Collectors.toList());
    }

    private static int compareJsonFiles(File first, File second) {
        var firstPath = first.getPath().replaceFirst("\\.json$", "");
        var secondPath = second.getPath().replaceFirst("\\.json$", "");
        return firstPath.compareTo(secondPath);
    }

    private static boolean isExcluded(File file, List<String> patterns) {
        return patterns.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .anyMatch(matcher -> matcher.matches(file.toPath().getFileName()));
    }

    @SneakyThrows
    private static Stream<Map<String, Object>> readFile(File file, Map<String, String> environmentVariables) {
        var s = Files.readString(file.toPath());
        for (var entry : environmentVariables.entrySet()) {
            s = s.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        var unresolvedVariable = UNRESOLVED_SUBSTITUTION.matcher(s);
        if (unresolvedVariable.find()) {
            throw new IllegalArgumentException(
                    "Unresolved definition environment variable " + unresolvedVariable.group() + " in " + file
            );
        }

        List<Map<String, Object>> entries = mapper.readValue(
                s,
                new TypeReference<List<Map<String, Object>>>() { }
        );

        return entries.stream().flatMap(JsonDefinitionReader::expandAccessControl);
    }

    // Preserve the original CCD_DEF environment substitutions for the folder-only API.
    private static JsonDefinitionImport defaultImport(String path) {
        var variables = new LinkedHashMap<String, String>();
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("CCD_DEF")) {
                variables.put(key, value);
            }
        });
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith("CCD_DEF"))
                .forEach(key -> variables.put(key, System.getProperty(key)));
        return new JsonDefinitionImport(path, null, variables, List.of());
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
        return toJson(defaultImport(path));
    }

    public static Map<String, List<Map<String, Object>>> toJson(JsonDefinitionImport request) {
        var templateSheetPaths = templateSheetPaths(request.template());
        var sheets = request.template() == null
                ? FILES
                : FILES.stream().filter(templateSheetPaths::containsKey).toList();
        return sheets.stream()
                .map(file -> new AbstractMap.SimpleEntry<>(
                        file,
                        JsonDefinitionReader.readPath(
                                resolveSheetPath(request.directory(), file, templateSheetPaths),
                                request
                        )
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
    private static Map<String, String> templateSheetPaths(String template) {
        if (template == null) {
            return Map.of();
        }

        var result = new HashMap<String, String>();
        try (var input = Files.newInputStream(Path.of(template)); var workbook = new XSSFWorkbook(input)) {
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
    private static Map<String, Set<String>> templateSheetHeaders(String template) {
        if (template == null) {
            return Map.of();
        }

        var result = new HashMap<String, Set<String>>();
        try (var input = Files.newInputStream(Path.of(template)); var workbook = new XSSFWorkbook(input)) {
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

    private static String pathFor(String root, String sheetName) {
        return Paths.get(root, sheetName).toString();
    }

    private static boolean pathExists(String path) {
        return Files.exists(Paths.get(path)) || Files.exists(Paths.get(path + ".json"));
    }

    public static Map<String, DefinitionSheet> fromJson(String path) {
        return fromJson(defaultImport(path));
    }

    public static Map<String, DefinitionSheet> fromJson(JsonDefinitionImport request) {
        return fromJson(request, null);
    }

    private static Map<String, DefinitionSheet> fromJson(JsonDefinitionImport request,
                                                        SpreadsheetValidator spreadsheetValidator) {
        Map<String, DefinitionSheet> result = new HashMap<>();
        var j = toJson(request);
        var templateHeaders = templateSheetHeaders(request.template());
        var templatePaths = templateSheetPaths(request.template());
        final var dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (String s : j.keySet()) {
            var sheet = j.get(s);
            var validationSheetName = templatePaths.getOrDefault(s, s);
            var defSheet = new DefinitionSheet();
            defSheet.setName(s);
            result.put(s, defSheet);
            for (var rowIndex = 0; rowIndex < sheet.size(); rowIndex++) {
                Map<String, Object> row = normalizeRoleColumn(sheet.get(rowIndex), templateHeaders.get(s));
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
                    if (spreadsheetValidator != null && val instanceof String stringValue) {
                        spreadsheetValidator.validate(validationSheetName, s1, stringValue, rowIndex + 4);
                    }
                    item.addAttribute(s1, val);
                }
            }
        }
        return result;
    }

    private static Map<String, Object> normalizeRoleColumn(Map<String, Object> row, Set<String> templateHeaders) {
        if (templateHeaders == null
                || !templateHeaders.contains("AccessProfile")
                || templateHeaders.contains("UserRole")
                || !row.containsKey("UserRole")) {
            return row;
        }

        var normalized = new LinkedHashMap<>(row);
        var userRole = normalized.remove("UserRole");
        normalized.putIfAbsent("AccessProfile", userRole);
        return normalized;
    }
}
