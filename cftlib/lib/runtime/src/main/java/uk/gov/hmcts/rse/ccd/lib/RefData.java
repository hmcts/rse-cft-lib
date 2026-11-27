package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reference data with the refData feature: the lists of values and case flags rd-commondata-api keeps, the court
 * venues rd-location-ref-api keeps and the judges rd-judicial-api keeps, which ExUI reads for case flags, case links
 * and its hearing journeys.
 *
 * <p>The lists, flags and venues are files under RSE_LIB_REFDATA_DIR, read on each request so edits apply without a
 * restart. Each file is either the body the real API returns, so a response from AAT can be saved as it is, or the
 * same rows as a JSON array:
 * <ul>
 *   <li>{@code lov/<category>.json}: rows of that category for any service, each naming its service in
 *       {@code service_id}, or none to apply to every service.</li>
 *   <li>{@code lov/<service id>/<category>.json}: rows for that service.</li>
 *   <li>{@code caseflags/<service id>.json}: the service's case flags.</li>
 *   <li>{@code court-venues.json}: court venues.</li>
 * </ul>
 * A row's {@code child_nodes} are rows too, as rd-commondata-api returns them with isChildRequired=Y.
 *
 * <p>Judges are the IDAM users a service names with {@code CFTLib.createJudicialUser}, so the judges people can sign in
 * as are the judges ExUI offers.
 */
public class RefData {
    static final String DIRECTORY = "RSE_LIB_REFDATA_DIR";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> LOV_FIELDS = List.of("category_key", "key", "value_en", "value_cy",
        "hint_text_en", "hint_text_cy", "lov_order", "parent_category", "parent_key", "active_flag");
    private static final List<String> LOV_OPTIONAL_FIELDS = List.of("external_reference_type", "external_reference");
    private static final Map<String, Person> PEOPLE = new LinkedHashMap<>();
    private static final Map<String, String> JUDGES = new LinkedHashMap<>();

    private final Path root;

    public RefData(Path root) {
        this.root = root;
    }

    public static RefData fromEnvironment() {
        var directory = System.getenv(DIRECTORY);
        return new RefData(Path.of(directory == null || directory.isBlank() ? "src/cftlib/refdata" : directory));
    }

    /**
     * Records an IDAM user's name, which a judge created from the user takes.
     */
    public static synchronized void recordIdamUser(String email, String id, String forename, String surname) {
        PEOPLE.put(email.toLowerCase(Locale.ROOT), new Person(id, forename, surname, email));
    }

    public static synchronized void addJudge(String email, String personalCode) {
        var key = email.toLowerCase(Locale.ROOT);
        if (!PEOPLE.containsKey(key)) {
            throw new IllegalArgumentException("No IDAM user " + email + ". Create the user with createIdamUser before"
                + " naming them as a judge.");
        }
        JUDGES.put(key, Objects.requireNonNull(personalCode, "personalCode"));
    }

    static synchronized void clearJudges() {
        PEOPLE.clear();
        JUDGES.clear();
    }

    /**
     * Lists of values, as rd-commondata-api's GET /refdata/commondata/lov/categories/{categoryId} answers them: a
     * service's own rows, or the rows with no service if it has none.
     *
     * @return the rows, or empty if no file has rows of the category
     */
    public List<ObjectNode> listOfValues(String category, String serviceId, String parentCategory, String parentKey,
                                         String key, String isChildRequired) {
        var table = lovTable();
        var childRequired = "Y".equalsIgnoreCase(isChildRequired) && parentCategory == null && parentKey == null;
        var rows = select(table, category, serviceId, parentCategory, parentKey, key, childRequired);
        if (rows.isEmpty() && serviceId != null) {
            rows = select(table, category, "", parentCategory, parentKey, key, childRequired);
        }
        var output = rows.stream()
            .sorted(Comparator.comparing((Row row) -> row.json().path("lov_order").isNumber()
                    ? row.json().path("lov_order").asLong() : Long.MAX_VALUE)
                .thenComparing(row -> row.json().path("value_en").asText("")))
            .toList();
        return childRequired ? nest(output) : output.stream().map(row -> lovJson(row, null)).toList();
    }

    /**
     * A service's case flags, as rd-commondata-api's GET /refdata/commondata/caseflags/service-id={id} answers them.
     *
     * @return the response, or empty if the service has no case flags, which rd-commondata-api answers with a 404
     */
    public Optional<ObjectNode> caseFlags(String serviceId, String flagType, String welshRequired,
                                          String availableExternalFlag) {
        var json = read(root.resolve("caseflags").resolve(serviceId.trim().toUpperCase(Locale.ROOT) + ".json"));
        if (json == null) {
            return Optional.empty();
        }
        var details = json.isArray() ? json : json.path("flags").path(0).path("FlagDetails");
        var flags = MAPPER.createArrayNode();
        for (var flag : details) {
            if (flagType == null || flagType.isBlank()
                || flag.path("name").asText().equalsIgnoreCase(flagType.trim())) {
                flags.add(flag.deepCopy());
            }
        }
        if (!yes(welshRequired)) {
            removeWelsh(flags);
        }
        if (yes(availableExternalFlag)) {
            removeInternal(flags);
        }
        if (flags.isEmpty()) {
            return Optional.empty();
        }
        var response = MAPPER.createObjectNode();
        response.putArray("flags").addObject().set("FlagDetails", flags);
        return Optional.of(response);
    }

    /**
     * Court venues, as rd-location-ref-api's GET /refdata/location/court-venues answers them.
     */
    public List<JsonNode> courtVenues(Map<String, String> query) {
        var epimmsIds = list(query.get("epimms_id"));
        return venues()
            .filter(venue -> epimmsIds.isEmpty() || epimmsIds.contains(venue.path("epimms_id").asText()))
            .filter(matches(query, "court_type_id", "court_type_id"))
            .filter(matches(query, "region_id", "region_id"))
            .filter(matches(query, "cluster_id", "cluster_id"))
            .filter(matches(query, "court_venue_name", "court_name"))
            .filter(locationFilters(query, "is_hearing_location", "is_case_management_location", "location_type",
                "is_temporary_location"))
            .toList();
    }

    /**
     * Court venues, as rd-location-ref-api's GET /refdata/location/court-venues/venue-search answers them: those whose
     * name, site, address or postcode contains the search string.
     */
    public List<JsonNode> searchCourtVenues(Map<String, String> query) {
        var term = query.getOrDefault("search-string", "").trim().toLowerCase(Locale.ROOT);
        var courtTypes = list(query.get("court-type-id"));
        return venues()
            .filter(venue -> Stream.of("court_name", "site_name", "court_address", "postcode")
                .anyMatch(field -> venue.path(field).asText("").toLowerCase(Locale.ROOT).contains(term)))
            .filter(venue -> courtTypes.isEmpty() || courtTypes.contains(venue.path("court_type_id").asText()))
            .filter(locationFilters(query, "is_hearing_location", "is_case_management_location", "location_type",
                "is_temporary_location"))
            .toList();
    }

    /**
     * Judges, as rd-judicial-api's POST /refdata/judicial/users answers a request naming them by IDAM id, object id
     * or personal code.
     */
    public List<ObjectNode> judicialUsers(JsonNode request) {
        var ids = new ArrayList<String>();
        Stream.of("sidam_ids", "object_ids", "personal_code")
            .forEach(field -> request.path(field).forEach(id -> ids.add(id.asText())));
        return judges().stream()
            .filter(judge -> ids.contains(judge.person().id()) || ids.contains(judge.personalCode()))
            .map(RefData::user)
            .toList();
    }

    /**
     * Judges, as rd-judicial-api's POST /refdata/judicial/users/search answers a search: those whose name or email
     * contains the search string.
     */
    public List<ObjectNode> searchJudicialUsers(JsonNode request) {
        var term = request.path("searchString").asText("").toLowerCase(Locale.ROOT);
        return judges().stream()
            .filter(judge -> fullName(judge.person()).toLowerCase(Locale.ROOT).contains(term)
                || judge.person().email().toLowerCase(Locale.ROOT).contains(term))
            .map(RefData::searchResult)
            .toList();
    }

    private static synchronized List<Judge> judges() {
        return JUDGES.entrySet().stream()
            .map(judge -> new Judge(PEOPLE.get(judge.getKey()), judge.getValue()))
            .toList();
    }

    private List<Row> lovTable() {
        var directory = root.resolve("lov");
        var rows = new ArrayList<Row>();
        if (!Files.isDirectory(directory)) {
            return rows;
        }
        try (var files = Files.walk(directory, 2)) {
            files.filter(file -> file.getFileName().toString().endsWith(".json"))
                .sorted()
                .forEach(file -> {
                    var name = file.getFileName().toString();
                    var category = name.substring(0, name.length() - ".json".length());
                    var service = file.getParent().equals(directory) ? null : file.getParent().getFileName().toString();
                    var json = read(file);
                    var values = json == null ? null : json.isArray() ? json : json.path("list_of_values");
                    if (values != null) {
                        flatten(values, category, service, rows);
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    private static void flatten(JsonNode values, String category, String service, List<Row> rows) {
        for (var value : values) {
            if (!value.isObject()) {
                continue;
            }
            var row = (ObjectNode) value.deepCopy();
            var children = row.remove("child_nodes");
            if (!row.hasNonNull("category_key")) {
                row.put("category_key", category);
            }
            var serviceId = row.hasNonNull("service_id") ? row.get("service_id").asText()
                : service == null ? "" : service;
            rows.add(new Row(row, serviceId));
            if (children != null && children.isArray()) {
                flatten(children, category, serviceId.isEmpty() ? null : serviceId, rows);
            }
        }
    }

    private static List<Row> select(List<Row> table, String category, String serviceId, String parentCategory,
                                    String parentKey, String key, boolean childRequired) {
        return table.stream().filter(row -> {
            var base = category.equals(text(row, "category_key"))
                && (serviceId == null || serviceId.equals(row.serviceId()))
                && (parentCategory == null || parentCategory.equals(text(row, "parent_category")))
                && (parentKey == null || parentKey.equals(text(row, "parent_key")))
                && (key == null || key.equals(text(row, "key")));
            var child = childRequired
                && category.equals(text(row, "parent_category"))
                && (key == null || key.equals(text(row, "parent_key")))
                && (serviceId == null || serviceId.equals(row.serviceId()));
            return base || child;
        }).toList();
    }

    // As rd-commondata-api: a row with no parent takes the rows that name it as their parent as its child_nodes. If
    // every row has a parent, they are returned as they are.
    private static List<ObjectNode> nest(List<Row> rows) {
        var parents = rows.stream().filter(row -> text(row, "parent_key").isEmpty()).toList();
        if (parents.isEmpty()) {
            return rows.stream().map(row -> lovJson(row, null)).toList();
        }
        return parents.stream().map(parent -> {
            var children = rows.stream()
                .filter(row -> !text(row, "parent_key").isEmpty()
                    && text(row, "parent_key").equals(text(parent, "key"))
                    && text(row, "parent_category").equals(text(parent, "category_key"))
                    && row.serviceId().equals(parent.serviceId()))
                .map(row -> lovJson(row, null))
                .toList();
            return lovJson(parent, children.isEmpty() ? null : children);
        }).toList();
    }

    private static ObjectNode lovJson(Row row, List<ObjectNode> children) {
        var json = MAPPER.createObjectNode();
        for (var field : LOV_FIELDS) {
            var value = row.json().get(field);
            if (value != null) {
                json.set(field, value);
            } else if (field.equals("active_flag")) {
                json.put(field, "Y");
            } else if (field.equals("lov_order")) {
                json.putNull(field);
            } else {
                json.put(field, "");
            }
        }
        for (var field : LOV_OPTIONAL_FIELDS) {
            var value = row.json().get(field);
            if (value != null && !value.asText("").isEmpty()) {
                json.set(field, value);
            }
        }
        if (children == null) {
            json.putNull("child_nodes");
        } else {
            json.putArray("child_nodes").addAll(children);
        }
        return json;
    }

    private static void removeWelsh(ArrayNode flags) {
        for (var flag : flags) {
            ((ObjectNode) flag).remove("name_cy");
            if (flag.path("childFlags").isArray()) {
                removeWelsh((ArrayNode) flag.get("childFlags"));
            }
        }
    }

    // As rd-commondata-api with available-external-flag=Y: only the flags people outside HMCTS may raise, without
    // the categories left empty.
    private static void removeInternal(ArrayNode flags) {
        for (var i = flags.size() - 1; i >= 0; i--) {
            var flag = flags.get(i);
            var children = flag.path("childFlags");
            if (children.isArray()) {
                removeInternal((ArrayNode) children);
            }
            var parent = flag.path("isParent").asBoolean(false);
            if (parent ? children.isEmpty() : !flag.path("externallyAvailable").asBoolean(false)) {
                flags.remove(i);
            }
        }
    }

    private Stream<JsonNode> venues() {
        var json = read(root.resolve("court-venues.json"));
        if (json == null) {
            return Stream.empty();
        }
        var venues = new ArrayList<JsonNode>();
        json.forEach(venues::add);
        return venues.stream();
    }

    private static Predicate<JsonNode> matches(Map<String, String> query, String parameter, String field) {
        var wanted = query.get(parameter);
        return venue -> wanted == null || wanted.isBlank()
            || list(wanted).stream().anyMatch(value -> value.equalsIgnoreCase(venue.path(field).asText()));
    }

    private static Predicate<JsonNode> locationFilters(Map<String, String> query, String... parameters) {
        return Arrays.stream(parameters)
            .map(parameter -> matches(query, parameter, parameter))
            .reduce(venue -> true, Predicate::and);
    }

    private static List<String> list(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private static boolean yes(String value) {
        return value != null && value.trim().equalsIgnoreCase("Y");
    }

    private static JsonNode read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read reference data from " + file, e);
        }
    }

    private record Row(ObjectNode json, String serviceId) {
    }

    private record Person(String id, String forename, String surname, String email) {
    }

    private record Judge(Person person, String personalCode) {
    }

    private static String text(Row row, String field) {
        return row.json().path(field).asText("");
    }

    private static List<String> names(Person person) {
        return Stream.of(person.forename(), person.surname()).filter(Objects::nonNull).filter(name -> !name.isBlank())
            .toList();
    }

    private static String fullName(Person person) {
        return String.join(" ", names(person));
    }

    private static String initials(Person person) {
        return names(person).stream().map(name -> name.substring(0, 1).toUpperCase(Locale.ROOT))
            .collect(Collectors.joining());
    }

    private static ObjectNode user(Judge judge) {
        var person = judge.person();
        var json = MAPPER.createObjectNode();
        json.put("sidam_id", person.id());
        json.put("object_id", person.id());
        json.put("known_as", person.forename());
        json.put("surname", person.surname());
        json.put("full_name", fullName(person));
        json.put("post_nominals", "");
        json.put("email_id", person.email());
        json.put("personal_code", judge.personalCode());
        json.put("title", "");
        json.put("initials", initials(person));
        json.putNull("retirement_date");
        json.put("active_flag", "Y");
        json.put("deleted_flag", "N");
        json.putArray("appointments");
        json.putArray("authorisations");
        json.putArray("roles");
        return json;
    }

    private static ObjectNode searchResult(Judge judge) {
        var person = judge.person();
        var json = MAPPER.createObjectNode();
        json.put("title", "");
        json.put("knownAs", person.forename());
        json.put("surname", person.surname());
        json.put("fullName", fullName(person));
        json.put("emailId", person.email());
        json.put("idamId", person.id());
        json.put("initials", initials(person));
        json.put("postNominals", "");
        json.put("personalCode", judge.personalCode());
        return json;
    }
}
