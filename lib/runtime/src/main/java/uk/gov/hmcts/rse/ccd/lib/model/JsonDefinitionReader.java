package uk.gov.hmcts.rse.ccd.lib.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.lingala.zip4j.util.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionDataItem;
import uk.gov.hmcts.ccd.definition.store.excel.parser.model.DefinitionSheet;
import uk.gov.hmcts.ccd.definition.store.excel.util.mapper.SheetName;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class JsonDefinitionReader {

    private static final List<String> FILES = Arrays.stream(SheetName.values())
            .map(SheetName::getName)
            .collect(Collectors.toList());
    private static final Date LIVE_FROM = new Date(1483228800000L);

    @Autowired
    private ObjectMapper mapper;

    @SneakyThrows
    public List<Map<String, String>> readPath(String path) {
        final var files = FileUtils.getFilesInDirectoryRecursive(Paths.get(path).toFile(), false, false);
        final var file = Paths.get(path + ".json").toFile();

        files.add(file);

        return files.parallelStream()
                .filter(f -> f.exists() && f.getName().endsWith(".json") && f.canRead())
                .flatMap(this::readFile)
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private Stream<Map<String, String>> readFile(File file) {
        List<Map<String, String>> entries = asList(mapper.readValue(file, Map[].class));

        return entries.stream();
    }

    @SneakyThrows
    public static Map<String, List<Map<String, String>>> toJson(final String path, JsonDefinitionReader reader) {
        return FILES.parallelStream()
                .map(file -> {
                    var p = file;
                    // quick hack since this sheet's name doesn't follow convention.
                    if (file.equals("EventToComplexTypes")) {
                        p = "CaseEventToComplexTypes";
                    }
                    return new AbstractMap.SimpleEntry<>(file, reader.readPath(path + "/" + p));
                })
                .collect(Collectors.toUnmodifiableMap(AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue));
    }

    public static Map<String, DefinitionSheet> fromJson(String path, JsonDefinitionReader reader) {
        Map<String, DefinitionSheet> result = new HashMap<>();
        var j = toJson(path, reader);
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
                    if (null != val) {
                        if (val.getClass().equals(Integer.class)) {
                            val = val.toString();
                        }
                        if (s1.equals("LiveFrom") || s1.equals("LiveTo")) {
                            var formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                            var ld = LocalDate.parse(val.toString(), formatter);
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
