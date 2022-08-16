package uk.gov.hmcts.rse.ccd.lib.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class JsonDefinitionReader {

    @Autowired
    private ObjectMapper mapper;

    public List<Map<String, String>> readPath(String path) {
        final var filesInDirectory = Paths.get(path).toFile().listFiles();
        final var files = filesInDirectory != null ? new ArrayList<>(asList(filesInDirectory)) : new ArrayList<File>();
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
}
