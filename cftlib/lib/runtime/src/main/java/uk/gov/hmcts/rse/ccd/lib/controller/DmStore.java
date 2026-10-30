package uk.gov.hmcts.rse.ccd.lib.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A stand-in for dm-store, the document store CDAM keeps documents in, which with local auth cftlib points CDAM at
 * (DM_STORE_BASE_URL). It answers the calls CDAM makes: upload, metadata and binary reads, the metadata PATCH that
 * attaches documents to a case, and delete.
 *
 * <p>Each document is a folder under RSE_LIB_DM_STORE_DIR holding its metadata and content, so documents last as
 * long as the case data that refers to them, across restarts.
 */
@Conditional(LocalAuthOnly.class)
@RestController
@RequestMapping(DmStore.PATH)
public class DmStore {
    public static final String PATH = "/dm-store/documents";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]+");
    private static final String METADATA = "metadata";

    private final Gson gson = new Gson();
    private final Path root = Path.of(System.getenv().getOrDefault("RSE_LIB_DM_STORE_DIR",
        Path.of(System.getProperty("java.io.tmpdir"), "cftlib-dm-store").toString()));

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> upload(ServerWebExchange exchange) {
        return exchange.getMultipartData().flatMap(parts -> {
            var classification = field(parts.getFirst("classification"));
            var metadata = new LinkedHashMap<String, String>();
            parts.forEach((name, values) -> {
                if (name.startsWith(METADATA + "[") && name.endsWith("]")) {
                    metadata.put(name.substring(METADATA.length() + 1, name.length() - 1), field(values.get(0)));
                }
            });
            var files = parts.getOrDefault("files", List.of()).stream()
                .filter(FilePart.class::isInstance)
                .map(FilePart.class::cast)
                .toList();
            return Flux.fromIterable(files)
                .concatMap(file -> store(exchange, file, classification, metadata))
                .collectList()
                .map(uploaded -> Map.<String, Object>of("_embedded", Map.of("documents", uploaded)));
        });
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> metadata(ServerWebExchange exchange, @PathVariable("id") String id)
        throws IOException {
        var document = load(id);
        return document == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(hal(exchange, id, document));
    }

    @GetMapping("/{id}/binary")
    public ResponseEntity<byte[]> binary(@PathVariable("id") String id) throws IOException {
        var document = load(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, String.valueOf(document.get("mimeType")))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + document.get("originalDocumentName") + "\"")
            .body(Files.readAllBytes(folder(id).resolve("content")));
    }

    /**
     * CDAM attaches documents to a case by adding the case's id, type and jurisdiction to their metadata.
     */
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateMetadata(@RequestBody Map<String, Object> body) throws IOException {
        if (body.get("documents") instanceof List<?> updates) {
            for (var update : updates) {
                if (update instanceof Map<?, ?> entry && entry.get(METADATA) instanceof Map<?, ?> changes) {
                    var id = String.valueOf(entry.get("documentId") != null
                        ? entry.get("documentId") : entry.get("document_id"));
                    var document = load(id);
                    if (document != null) {
                        @SuppressWarnings("unchecked")
                        var metadata = (Map<String, Object>) document.get(METADATA);
                        changes.forEach((name, value) -> metadata.put(String.valueOf(name), String.valueOf(value)));
                        save(id, document);
                    }
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws IOException {
        if (load(id) == null) {
            return ResponseEntity.notFound().build();
        }
        try (var files = Files.list(folder(id))) {
            for (var file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.delete(folder(id));
        return ResponseEntity.noContent().build();
    }

    private Mono<Map<String, Object>> store(ServerWebExchange exchange, FilePart file, String classification,
                                            Map<String, String> metadata) {
        var id = UUID.randomUUID().toString();
        return Mono.fromCallable(() -> Files.createDirectories(folder(id)))
            .then(file.transferTo(folder(id).resolve("content")))
            .then(Mono.fromCallable(() -> {
                var document = new LinkedHashMap<String, Object>();
                document.put("classification", classification);
                document.put("size", Files.size(folder(id).resolve("content")));
                document.put("mimeType", file.headers().getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.headers().getContentType().toString());
                document.put("originalDocumentName", file.filename());
                var now = Instant.now().toString();
                // CDAM sends the user it acts for, which dm-store records.
                var user = exchange.getRequest().getHeaders().getFirst("user-id");
                document.put("createdOn", now);
                document.put("createdBy", user);
                document.put("modifiedOn", now);
                document.put("lastModifiedBy", user);
                document.put(METADATA, new LinkedHashMap<>(metadata));
                save(id, document);
                return hal(exchange, id, document);
            }));
    }

    private Map<String, Object> hal(ServerWebExchange exchange, String id, Map<String, Object> document) {
        var uri = exchange.getRequest().getURI();
        var self = uri.getScheme() + "://" + uri.getAuthority() + PATH + "/" + id;
        var hal = new LinkedHashMap<>(document);
        // Gson reads numbers back as doubles.
        hal.put("size", ((Number) document.get("size")).longValue());
        hal.put("ttl", Instant.parse(String.valueOf(document.get("createdOn"))).plus(1, ChronoUnit.DAYS).toString());
        hal.put("_links", Map.of("self", Map.of("href", self), "binary", Map.of("href", self + "/binary")));
        return hal;
    }

    private static String field(Object part) {
        return part instanceof FormFieldPart field ? field.value() : null;
    }

    private Path folder(String id) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Not a document id: " + id);
        }
        return root.resolve(id);
    }

    private void save(String id, Map<String, Object> document) throws IOException {
        Files.writeString(folder(id).resolve("metadata.json"), gson.toJson(document), StandardCharsets.UTF_8);
    }

    private Map<String, Object> load(String id) throws IOException {
        var file = folder(id).resolve("metadata.json");
        if (!Files.exists(file)) {
            return null;
        }
        return gson.fromJson(Files.readString(file, StandardCharsets.UTF_8),
            new TypeToken<LinkedHashMap<String, Object>>() { }.getType());
    }
}
