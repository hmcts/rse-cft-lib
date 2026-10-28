package uk.gov.hmcts.rse.ccd.lib;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Translates the subset of the elasticsearch query language that CCD issues into SQL over
 * {@link EsStubSources}' document view.
 *
 * <p>Anything outside that subset is rejected with an {@link EsStubException} rather than ignored:
 * a clause that was silently dropped would let a search test pass locally and fail against a real
 * elasticsearch.
 *
 * <p>Fields that elasticsearch analyses (the {@code text} mapped metadata and case data) are
 * matched against their tokens, so {@code term} on {@code state} matches a stored value of
 * {@code Draft} just as it does in elasticsearch. A {@code .keyword} sub-field is matched whole,
 * lowercased and ascii folded by the {@code lowercase_normalizer} CCD gives it.
 *
 * <p>What the translator cannot know is a case data field's own type, which it would need to tell
 * a {@code Postcode} from a {@code Text}, so every path under {@code data} is matched as though it
 * were {@code Text}. The README names the types that leaves wrong.
 */
class EsQueryTranslator {

    /**
     * Splits a value into the tokens the standard analyzer would produce. Underscores join words
     * under the unicode segmentation rules the standard tokenizer follows, so a case type id such
     * as {@code CARE_SUPERVISION_EPO} remains a single token.
     */
    static final String TOKEN_SEPARATOR_REGEX = "[^[:alnum:]_]+";
    // Matched against postgres, which applies its locale: an ASCII-only class here would
    // tokenise an accented name differently from the column it is compared with.
    private static final String WORD = "\\p{IsAlphabetic}\\p{IsDigit}";
    private static final String TOKEN_SEPARATOR_JAVA_REGEX = "[^" + WORD + "_]+";

    /**
     * The characters the standard tokenizer keeps inside a token when a word character sits on
     * either side of them: the apostrophe of {@code o'brien}, the dot of {@code john.smith}, the
     * separators of {@code 1,234.5}. Dropping them instead of keeping them tokenises a stored
     * value and a query term the same way, so the same documents match even though the tokens
     * themselves read differently from elasticsearch's.
     *
     * <p>The doubled apostrophe in the postgres form is SQL quoting, not part of the class.
     */
    private static final String WORD_JOINER_REGEX = "([[:alnum:]])[''.,]([[:alnum:]])";
    private static final String WORD_JOINER_JAVA_REGEX = "([" + WORD + "])['.,]([" + WORD + "])";
    /**
     * The same, in a wildcard pattern, where {@code *} and {@code ?} stand in for word characters.
     */
    private static final String WORD_JOINER_PATTERN_REGEX = "([" + WORD + "*?])['.,]([" + WORD + "*?])";

    private static final String KEYWORD_SUFFIX = ".keyword";

    /**
     * The accents CCD's {@code lowercase_normalizer} folds away, as a pair of equal length
     * strings so that postgres and this translator fold a value identically. Elasticsearch's
     * asciifolding covers more of unicode, and the characters it expands rather than maps —
     * {@code ß} to {@code ss}, {@code æ} to {@code ae} — are left alone here.
     */
    private static final String ACCENTED = "àáâãäåèéêëìíîïòóôõöøùúûüýÿñçÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖØÙÚÛÜÝÑÇ";
    private static final String UNACCENTED = "aaaaaaeeeeiiiioooooouuuuyyncAAAAAAEEEEIIIIOOOOOOUUUUYNC";

    /** A date with no time of day, which elasticsearch rounds to an end of the day. */
    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final String TABLE = "u";

    private static final Set<String> SUPPORTED_BODY_KEYS = Set.of(
        "query", "from", "size", "sort", "_source", "track_total_hits", "timeout");

    private static final Set<String> SUPPORTED_BOOL_KEYS = Set.of(
        "must", "filter", "should", "must_not", "minimum_should_match", "boost", "_name",
        "adjust_pure_negative");

    private final ObjectMapper mapper;
    private int nestedAliases;

    EsQueryTranslator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    enum ColumnType {
        TEXT,
        NUMBER,
        DATE
    }

    /**
     * A metadata column of the document view, with the elasticsearch mapping that governs how it
     * is matched.
     */
    private record Column(String name, ColumnType type, boolean analysed) {
    }

    private static final Map<String, Column> COLUMNS = Map.of(
        "reference", new Column("reference", ColumnType.NUMBER, true),
        "state", new Column("state", ColumnType.TEXT, true),
        "jurisdiction", new Column("jurisdiction", ColumnType.TEXT, true),
        "case_type_id", new Column("case_type_id", ColumnType.TEXT, true),
        "created_date", new Column("created_date", ColumnType.DATE, false),
        "last_modified", new Column("last_modified", ColumnType.DATE, false),
        "last_state_modified_date", new Column("last_state_modified_date", ColumnType.DATE, false),
        // Mapped as a keyword, so never analysed.
        "security_classification", new Column("security_classification", ColumnType.TEXT, false),
        "id", new Column("id", ColumnType.NUMBER, false));

    private static final String REFERENCE = "reference";

    /**
     * The jsonb columns a field path can address, and whether CCD maps what is under them with
     * {@code lowercase_normalizer}. It maps {@code data} from the case type's field types;
     * {@code data_classification} is a plain keyword per field, and {@code supplementary_data}
     * is left to elasticsearch's dynamic mapping, so neither is normalised.
     */
    private static final Map<String, String> JSON_ROOTS = Map.of(
        "data", TABLE + ".data",
        "data_classification", TABLE + ".data_classification",
        "supplementary_data", TABLE + ".supplementary_data");

    private static final Set<String> NORMALISED_ROOTS = Set.of("data");

    /** A plain keyword is matched byte for byte, so its value is never tokenised either. */
    private static final Set<String> KEYWORD_ROOTS = Set.of("data_classification");

    /**
     * The context a field name is resolved in. Inside a {@code nested} clause the root is a single
     * element of the nested array and field names are relative to the nested path.
     */
    private record Scope(String jsonRoot, String pathPrefix, boolean normalised) {
    }

    private static final Scope ROOT_SCOPE = new Scope(null, "", false);

    /**
     * A resolved field: either a column of the document view or a json path into one of its
     * jsonb columns. {@code normalised} says whether elasticsearch holds the whole value
     * lowercased and ascii folded, which is how CCD maps every keyword it declares itself.
     */
    private record Field(String name,
                         Column column,
                         String jsonRoot,
                         String jsonPath,
                         boolean analysed,
                         boolean normalised) {
    }

    private static boolean isNested(Scope scope) {
        return scope.jsonRoot() != null;
    }

    private static boolean isColumn(Field field) {
        return field.column() != null;
    }

    /** A sort key, already rendered to SQL. */
    record Sort(String expression, List<Object> params, boolean ascending) {
    }

    /** A translated search body. */
    record Search(String where,
                  List<Object> whereParams,
                  List<Sort> sorts,
                  int from,
                  int size,
                  List<String> sourceIncludes,
                  boolean sourceEnabled) {
    }

    private static final class Params {
        private final List<Object> values = new ArrayList<>();

        String bind(Object value) {
            values.add(value);
            return "?";
        }

        List<Object> values() {
            return values;
        }
    }

    Search translate(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw EsStubException.badRequest("the search body must be a json object");
        }
        for (var field : fieldNames(body)) {
            if (!SUPPORTED_BODY_KEYS.contains(field)) {
                throw EsStubException.unsupported(field, "It appeared at the top level of a search body.");
            }
        }

        var params = new Params();
        nestedAliases = 0;
        var where = body.has("query")
            ? query(body.get("query"), ROOT_SCOPE, params)
            : "true";

        var sorts = sorts(body.get("sort"));
        var from = intValue(body.get("from"), 0, "from");
        var size = intValue(body.get("size"), 10, "size");

        var source = body.get("_source");
        // _source: false means a hit carries no document at all, not every field of one.
        var sourceEnabled = source == null || !source.isBoolean() || source.asBoolean();
        return new Search(where, params.values(), sorts, from, size, sourceIncludes(source), sourceEnabled);
    }

    private String query(JsonNode node, Scope scope, Params params) {
        if (node == null || node.isNull()) {
            return "true";
        }
        if (!node.isObject()) {
            throw EsStubException.badRequest("a query clause must be a json object, got " + node.getNodeType());
        }
        var names = fieldNames(node);
        if (names.size() != 1) {
            throw EsStubException.badRequest(
                "a query clause must have exactly one key, got " + names);
        }
        var clause = names.get(0);
        var body = node.get(clause);
        return switch (clause) {
            case "match_all" -> "true";
            case "match_none" -> "false";
            case "bool" -> bool(body, scope, params);
            case "wrapper" -> query(decodeWrapper(body), scope, params);
            case "nested" -> nested(body, scope, params);
            case "term" -> term(body, scope, params);
            case "terms" -> terms(body, scope, params);
            case "match" -> match(body, scope, params);
            case "match_phrase" -> matchPhrase(body, scope, params);
            case "match_phrase_prefix" -> matchPhrasePrefix(body, scope, params);
            case "wildcard" -> wildcard(body, scope, params);
            case "range" -> range(body, scope, params);
            case "exists" -> exists(body, scope, params);
            default -> throw EsStubException.unsupported(clause, "It appeared as a query clause.");
        };
    }

    private String bool(JsonNode node, Scope scope, Params params) {
        requireObject(node, "bool");
        for (var name : fieldNames(node)) {
            if (!SUPPORTED_BOOL_KEYS.contains(name)) {
                throw EsStubException.unsupported(name, "It appeared inside a bool query.");
            }
        }

        var musts = clauses(node.get("must"), "must");
        var filters = clauses(node.get("filter"), "filter");

        var conjuncts = new ArrayList<String>();
        for (var clause : musts) {
            conjuncts.add(query(clause, scope, params));
        }
        for (var clause : filters) {
            conjuncts.add(query(clause, scope, params));
        }
        for (var clause : clauses(node.get("must_not"), "must_not")) {
            // A metadata column predicate is null when the column is, and elasticsearch treats a
            // missing field as not matching the clause, so the row must survive the negation.
            conjuncts.add("not coalesce(" + query(clause, scope, params) + ", false)");
        }

        var shouldClauses = clauses(node.get("should"), "should");
        var minimumShouldMatch = minimumShouldMatch(node.get("minimum_should_match"),
            !musts.isEmpty() || !filters.isEmpty());

        // Translated only where the result is used. Translating binds the clause's values, and a
        // clause whose sql is then discarded would leave them bound with no placeholder to fill,
        // so the statement would be given more values than it has parameters.
        if (!shouldClauses.isEmpty()) {
            if (minimumShouldMatch > shouldClauses.size()) {
                conjuncts.add("false");
            } else if (minimumShouldMatch >= 1) {
                var shoulds = new ArrayList<String>();
                for (var clause : shouldClauses) {
                    shoulds.add(query(clause, scope, params));
                }
                if (minimumShouldMatch == 1) {
                    conjuncts.add("(" + String.join(" or ", shoulds) + ")");
                } else {
                    var counted = shoulds.stream()
                        .map(s -> "(case when " + s + " then 1 else 0 end)")
                        .toList();
                    conjuncts.add("(" + String.join(" + ", counted) + ") >= " + minimumShouldMatch);
                }
            }
            // Otherwise none of them has to match: alongside a must or filter they only affect
            // the score, which this stub does not produce.
        }

        return conjuncts.isEmpty() ? "true" : "(" + String.join(" and ", conjuncts) + ")";
    }

    /**
     * Elasticsearch defaults minimum_should_match to one when a bool has should clauses and no
     * must or filter clauses, and to zero otherwise.
     */
    private int minimumShouldMatch(JsonNode node, boolean hasMustOrFilter) {
        if (node == null || node.isNull()) {
            return hasMustOrFilter ? 0 : 1;
        }
        if (node.isInt() || node.isLong()) {
            return requireNonNegative(node.asInt());
        }
        if (node.isTextual()) {
            try {
                return requireNonNegative(Integer.parseInt(node.asText().trim()));
            } catch (NumberFormatException e) {
                throw EsStubException.unsupported("minimum_should_match=" + node.asText(),
                    "Only a non-negative whole number is supported.");
            }
        }
        throw EsStubException.unsupported("minimum_should_match", "Only a non-negative whole number is supported.");
    }

    private int requireNonNegative(int value) {
        if (value < 0) {
            throw EsStubException.unsupported("minimum_should_match=" + value,
                "Only a non-negative whole number is supported.");
        }
        return value;
    }

    private JsonNode decodeWrapper(JsonNode node) {
        requireObject(node, "wrapper");
        var encoded = node.get("query");
        if (encoded == null || !encoded.isTextual()) {
            throw EsStubException.badRequest("a wrapper query must carry a base64 'query'");
        }
        try {
            var decoded = new String(Base64.getDecoder().decode(encoded.asText()), StandardCharsets.UTF_8);
            return mapper.readTree(decoded);
        } catch (Exception e) {
            throw EsStubException.badRequest("could not decode the base64 json of a wrapper query: " + e.getMessage());
        }
    }

    private String nested(JsonNode node, Scope scope, Params params) {
        requireObject(node, "nested");
        if (isNested(scope)) {
            throw EsStubException.unsupported("nested inside nested", "Only a single level of nesting is supported.");
        }
        for (var name : fieldNames(node)) {
            // score_mode and ignore_unmapped only affect scoring, which the stub does not produce.
            if (!Set.of("path", "query", "score_mode", "ignore_unmapped", "boost", "_name").contains(name)) {
                throw EsStubException.unsupported(name, "It appeared inside a nested query.");
            }
        }
        var path = node.get("path");
        if (path == null || !path.isTextual()) {
            throw EsStubException.badRequest("a nested query must carry a 'path'");
        }
        var pathField = field(path.asText(), scope);
        if (isColumn(pathField)) {
            throw EsStubException.unsupported("nested on '" + path.asText() + "'",
                "A nested path must address case data, not a metadata column.");
        }
        if (!path.asText().endsWith(".value")) {
            // A path addressing the collection itself yields one item, the array, and the inner
            // predicates then unwrap it again: fields could match in different elements, which
            // is what nesting exists to prevent. CCD addresses the element, as in
            // 'data.SearchCriteria.SearchParties.value'.
            throw EsStubException.unsupported("nested on '" + path.asText() + "'",
                "A nested path must address a collection's element, ending in '.value'.");
        }

        var alias = "n" + nestedAliases++;
        var pathParam = params.bind(pathField.jsonPath());
        var inner = query(node.get("query"),
            new Scope(alias + ".elem", path.asText() + ".", pathField.normalised()), params);

        return "exists (select 1 from jsonb_array_elements(jsonb_path_query_array("
            + pathField.jsonRoot() + ", " + pathParam + "::jsonpath)) as " + alias + "(elem) where " + inner + ")";
    }

    private String term(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "term");
        var field = field(entry.getKey(), scope);
        var value = scalar(entry.getValue(), "term", Set.of("value"));
        return matchTerm(field, value, params);
    }

    private String terms(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "terms");
        var field = field(entry.getKey(), scope);
        var values = entry.getValue();
        if (!values.isArray()) {
            throw EsStubException.unsupported("terms on '" + entry.getKey() + "'",
                "Only an inline array of values is supported, not a terms lookup.");
        }
        if (values.isEmpty()) {
            return "false";
        }
        var disjuncts = new ArrayList<String>();
        for (var value : values) {
            disjuncts.add(matchTerm(field, text(value, "terms"), params));
        }
        return "(" + String.join(" or ", disjuncts) + ")";
    }

    private String matchTerm(Field field, String value, Params params) {
        if (isCaseReference(field)) {
            return caseReference(field, value, params);
        }
        if (!field.analysed()) {
            return wholeValue(field, value, params);
        }
        return tokenHeld(field, value.toLowerCase(Locale.ROOT), params);
    }

    private String match(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "match");
        var field = field(entry.getKey(), scope);
        var spec = entry.getValue();
        var and = false;
        var allOnZeroTerms = false;
        if (spec.isObject()) {
            for (var name : fieldNames(spec)) {
                switch (name) {
                    case "query", "operator", "analyzer", "boost", "_name", "lenient", "zero_terms_query" -> { }
                    default -> throw EsStubException.unsupported(name, "It appeared inside a match query.");
                }
            }
            var operator = spec.get("operator");
            and = operator != null && "and".equalsIgnoreCase(operator.asText());
            var zeroTerms = spec.get("zero_terms_query");
            allOnZeroTerms = zeroTerms != null && "all".equalsIgnoreCase(zeroTerms.asText());
        }
        var value = scalar(spec, "match", Set.of("query"));

        if (isCaseReference(field)) {
            return caseReference(field, value, params);
        }
        if (!field.analysed()) {
            return wholeValue(field, value, params);
        }
        var tokens = tokenise(value);
        if (tokens.isEmpty()) {
            return allOnZeroTerms ? "true" : "false";
        }
        var parts = new ArrayList<String>();
        for (var token : tokens) {
            parts.add(tokenHeld(field, token, params));
        }
        return "(" + String.join(and ? " and " : " or ", parts) + ")";
    }

    private String matchPhrase(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "match_phrase");
        var field = field(entry.getKey(), scope);
        rejectSlop(entry.getValue(), "match_phrase");
        var value = scalar(entry.getValue(), "match_phrase", Set.of("query"));

        if (isCaseReference(field)) {
            return caseReference(field, value, params);
        }
        if (!field.analysed()) {
            return wholeValue(field, value, params);
        }
        var tokens = tokenise(value);
        if (tokens.isEmpty()) {
            return "false";
        }
        var needle = " " + String.join(" ", tokens) + " ";
        return scalarPredicate(field, params,
            expression -> "strpos(" + analysedPhrase(expression) + ", " + params.bind(needle) + ") > 0");
    }

    private String matchPhrasePrefix(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "match_phrase_prefix");
        var field = field(entry.getKey(), scope);
        rejectSlop(entry.getValue(), "match_phrase_prefix");
        var value = scalar(entry.getValue(), "match_phrase_prefix", Set.of("query"));

        if (isCaseReference(field)) {
            return like(field, likeEscape(hyphenless(value)) + "%", params);
        }
        if (!field.analysed()) {
            return like(field, likeEscape(matchable(field, value)) + "%", params);
        }
        var tokens = tokenise(value);
        if (tokens.isEmpty()) {
            return "false";
        }
        // Tokens only ever contain alphanumerics and underscores, so need no regex escaping.
        var pattern = " " + String.join(" ", tokens) + "[^ ]* ";
        return scalarPredicate(field, params,
            expression -> analysedPhrase(expression) + " ~ " + params.bind(pattern));
    }

    private String wildcard(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "wildcard");
        var field = field(entry.getKey(), scope);
        var spec = entry.getValue();
        if (spec.isObject()) {
            for (var name : fieldNames(spec)) {
                switch (name) {
                    case "wildcard", "value", "boost", "_name", "rewrite" -> { }
                    default -> throw EsStubException.unsupported(name, "It appeared inside a wildcard query.");
                }
            }
        }
        var pattern = scalar(spec, "wildcard", Set.of("wildcard", "value"));

        if (!field.analysed()) {
            // A wildcard is not analysed, but it runs against terms that the normalizer has
            // already folded, so the pattern has to be folded to meet them.
            return like(field, wildcardToLike(matchable(field, pattern)), params);
        }
        var tokenPattern = wildcardToLike(
            squashJoiners(pattern.toLowerCase(Locale.ROOT), WORD_JOINER_PATTERN_REGEX));
        return scalarPredicate(field, params, expression ->
            "exists (select 1 from unnest(" + tokensOf(expression) + ") as t(token) where t.token like "
                + params.bind(tokenPattern) + " escape '\\')");
    }

    private String range(JsonNode node, Scope scope, Params params) {
        var entry = singleField(node, "range");
        var field = field(entry.getKey(), scope);
        var spec = entry.getValue();
        if (!spec.isObject()) {
            throw EsStubException.badRequest("a range query must carry an object of bounds");
        }

        var bounds = new ArrayList<String>();
        var includeLower = booleanValue(spec.get("include_lower"), true);
        var includeUpper = booleanValue(spec.get("include_upper"), true);
        for (var name : fieldNames(spec)) {
            switch (name) {
                case "gt" -> bounds.add(compare(field, ">", text(spec.get(name), "range"), params));
                case "gte" -> bounds.add(compare(field, ">=", text(spec.get(name), "range"), params));
                case "lt" -> bounds.add(compare(field, "<", text(spec.get(name), "range"), params));
                case "lte" -> bounds.add(compare(field, "<=", text(spec.get(name), "range"), params));
                case "from" -> {
                    if (!spec.get(name).isNull()) {
                        bounds.add(compare(field, includeLower ? ">=" : ">", text(spec.get(name), "range"), params));
                    }
                }
                case "to" -> {
                    if (!spec.get(name).isNull()) {
                        bounds.add(compare(field, includeUpper ? "<=" : "<", text(spec.get(name), "range"), params));
                    }
                }
                case "include_lower", "include_upper", "boost", "_name" -> { }
                default -> throw EsStubException.unsupported(name, "It appeared inside a range query.");
            }
        }
        return bounds.isEmpty() ? "true" : "(" + String.join(" and ", bounds) + ")";
    }

    private String exists(JsonNode node, Scope scope, Params params) {
        requireObject(node, "exists");
        for (var name : fieldNames(node)) {
            if (!Set.of("field", "boost", "_name").contains(name)) {
                throw EsStubException.unsupported(name, "It appeared inside an exists query.");
            }
        }
        var fieldName = node.get("field");
        if (fieldName == null || !fieldName.isTextual()) {
            throw EsStubException.badRequest("an exists query must carry a 'field'");
        }
        var field = field(fieldName.asText(), scope);
        if (isColumn(field)) {
            return TABLE + "." + field.column().name() + " is not null";
        }
        var path = params.bind(field.jsonPath());
        return "exists (select 1 from jsonb_array_elements(jsonb_path_query_array(" + field.jsonRoot() + ", "
            + path + "::jsonpath)) as e(value) where e.value is not null and e.value <> 'null'::jsonb)";
    }

    private String compare(Field field, String operator, String value, Params params) {
        if (isColumn(field)) {
            var column = TABLE + "." + field.column().name();
            return switch (field.column().type()) {
                case NUMBER -> "=".equals(operator)
                    ? column + "::text = " + params.bind(value)
                    : column + " " + operator + " " + params.bind(value) + "::numeric";
                case DATE -> column + " " + operator + " "
                    + params.bind(dateBound(value, operator)) + "::timestamp";
                case TEXT -> column + " " + operator + " " + params.bind(value);
            };
        }
        return scalarPredicate(field, params, v -> v + " " + operator + " " + params.bind(value));
    }

    /**
     * Elasticsearch rounds a bound given as a date with no time of day to the end of that day
     * that includes the whole of it, so a case modified at noon is inside
     * {@code lte: 2024-01-15}.
     */
    private static String dateBound(String value, String operator) {
        if (!DATE_ONLY.matcher(value).matches()) {
            return value;
        }
        return switch (operator) {
            case "<=", ">" -> value + "T23:59:59.999";
            default -> value;
        };
    }

    /** Matches a field elasticsearch holds whole, rather than as tokens. */
    private String wholeValue(Field field, String value, Params params) {
        if (!field.normalised()) {
            return compare(field, "=", value, params);
        }
        return scalarPredicate(field, params,
            expression -> normalised(expression) + " = " + params.bind(normalise(value)));
    }

    private String like(Field field, String pattern, Params params) {
        return scalarPredicate(field, params, expression ->
            (field.normalised() ? normalised(expression) : expression)
                + " like " + params.bind(pattern) + " escape '\\'");
    }

    /** The form of a query value that can be compared with a whole stored value. */
    private String matchable(Field field, String value) {
        return field.normalised() ? normalise(value) : value;
    }

    /**
     * CCD gives {@code reference} a search analyzer that strips hyphens and leaves the value
     * whole, so a case reference matches however the user grouped its digits.
     */
    private static boolean isCaseReference(Field field) {
        return isColumn(field) && field.analysed() && REFERENCE.equals(field.column().name());
    }

    private String caseReference(Field field, String value, Params params) {
        return TABLE + "." + field.column().name() + "::text = " + params.bind(hyphenless(value));
    }

    private static String hyphenless(String value) {
        return value.replace("-", "");
    }

    private String tokenHeld(Field field, String lowercasedToken, Params params) {
        return scalarPredicate(field, params,
            expression -> params.bind(lowercasedToken) + " = any(" + tokensOf(expression) + ")");
    }

    /**
     * Applies a predicate to a field's value, which the predicate receives as a SQL text
     * expression. A column has a single value; a json path can resolve to many, and matches when
     * any of them satisfies the predicate, as in elasticsearch.
     */
    private String scalarPredicate(Field field, Params params, Function<String, String> predicate) {
        if (isColumn(field)) {
            var column = TABLE + "." + field.column().name();
            return predicate.apply(field.column().type() == ColumnType.TEXT ? column : column + "::text");
        }
        var path = params.bind(field.jsonPath());
        var inner = predicate.apply("v.val");
        return "exists (select 1 from jsonb_array_elements_text(jsonb_path_query_array(" + field.jsonRoot() + ", "
            + path + "::jsonpath)) as v(val) where " + inner + ")";
    }

    private String tokensOf(String expression) {
        return "regexp_split_to_array(" + analysed(expression) + ", '" + TOKEN_SEPARATOR_REGEX + "')";
    }

    private String analysedPhrase(String expression) {
        return "(' ' || array_to_string(regexp_split_to_array(" + analysed(expression) + ", '"
            + TOKEN_SEPARATOR_REGEX + "'), ' ') || ' ')";
    }

    /** A value in the form the standard analyzer leaves it in, before it is split into tokens. */
    private String analysed(String expression) {
        return "regexp_replace(lower(" + expression + "), '" + WORD_JOINER_REGEX + "', '\\1\\2', 'g')";
    }

    /**
     * A value in the form {@code lowercase_normalizer} leaves it in.
     */
    private String normalised(String expression) {
        return "lower(translate(" + expression + ", '" + ACCENTED + "', '" + UNACCENTED + "'))";
    }

    private Field field(String rawName, Scope scope) {
        var name = rawName;
        var analysed = true;
        if (name.endsWith(KEYWORD_SUFFIX)) {
            name = name.substring(0, name.length() - KEYWORD_SUFFIX.length());
            analysed = false;
        }
        if (name.isEmpty()) {
            throw EsStubException.badRequest("a query clause named an empty field");
        }

        if (isNested(scope)) {
            if (!name.startsWith(scope.pathPrefix())) {
                throw EsStubException.unsupported("field '" + rawName + "'",
                    "Inside a nested query every field must sit under the nested path '"
                        + scope.pathPrefix() + "'.");
            }
            return jsonField(rawName, scope.jsonRoot(),
                name.substring(scope.pathPrefix().length()), analysed, scope.normalised());
        }

        var column = COLUMNS.get(name);
        if (column != null) {
            // Only the text columns carry the normalizer; a number or a date has nothing to fold.
            return new Field(rawName, column, null, null, analysed && column.analysed(),
                column.type() == ColumnType.TEXT);
        }

        for (var root : JSON_ROOTS.entrySet()) {
            var normalised = NORMALISED_ROOTS.contains(root.getKey());
            var tokenised = analysed && !KEYWORD_ROOTS.contains(root.getKey());
            if (name.equals(root.getKey())) {
                return jsonField(rawName, root.getValue(), "", tokenised, normalised);
            }
            if (name.startsWith(root.getKey() + ".")) {
                return jsonField(rawName, root.getValue(),
                    name.substring(root.getKey().length() + 1), tokenised, normalised);
            }
        }

        throw EsStubException.unsupported("field '" + rawName + "'",
            "The stub knows the metadata columns " + COLUMNS.keySet()
                + " and paths under " + JSON_ROOTS.keySet() + ".");
    }

    private Field jsonField(String name, String root, String path, boolean analysed, boolean normalised) {
        return new Field(name, null, root, jsonPath(path), analysed, normalised);
    }

    /**
     * Renders a dotted field path as a postgres jsonpath. Lax mode unwraps arrays as it walks, so
     * a path through a CCD collection resolves to every element's value, as elasticsearch does.
     */
    private String jsonPath(String path) {
        var sql = new StringBuilder("lax $");
        if (!path.isEmpty()) {
            for (var segment : path.split("\\.", -1)) {
                if (segment.isEmpty()) {
                    throw EsStubException.badRequest("field path '" + path + "' has an empty segment");
                }
                sql.append(".\"").append(escapeJsonPath(segment)).append('"');
            }
        }
        return sql.toString();
    }

    private String escapeJsonPath(String segment) {
        var escaped = new StringBuilder();
        for (var c : segment.toCharArray()) {
            if (c < 0x20) {
                throw EsStubException.badRequest("field path segment '" + segment + "' has a control character");
            }
            if (c == '"' || c == '\\') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private List<Sort> sorts(JsonNode node) {
        var sorts = new ArrayList<Sort>();
        if (node == null || node.isNull()) {
            return sorts;
        }
        var elements = node.isArray() ? node : mapper.createArrayNode().add(node);
        for (var element : elements) {
            var sort = sort(element);
            if (sort != null) {
                sorts.add(sort);
            }
        }
        return sorts;
    }

    private Sort sort(JsonNode element) {
        if (element.isTextual()) {
            return sort(element.asText(), true);
        }
        if (!element.isObject()) {
            throw EsStubException.badRequest("a sort entry must be a field name or an object");
        }
        var entry = singleField(element, "sort");
        var spec = entry.getValue();
        var ascending = true;
        if (spec.isTextual()) {
            ascending = !"desc".equalsIgnoreCase(spec.asText());
        } else if (spec.isObject()) {
            for (var name : fieldNames(spec)) {
                // 'missing' and 'unmapped_type' only move absent values, which the stub always sorts last.
                if (!Set.of("order", "missing", "unmapped_type").contains(name)) {
                    throw EsStubException.unsupported(name, "It appeared inside a sort entry.");
                }
            }
            var order = spec.get("order");
            ascending = order == null || !"desc".equalsIgnoreCase(order.asText());
        } else {
            throw EsStubException.badRequest("a sort direction must be a string or an object");
        }
        return sort(entry.getKey(), ascending);
    }

    private Sort sort(String fieldName, boolean ascending) {
        if ("_score".equals(fieldName) || "_doc".equals(fieldName)) {
            // The stub does not score, and every document has the same relevance.
            return null;
        }
        var params = new Params();
        var field = field(fieldName, ROOT_SCOPE);
        String expression;
        if (isColumn(field)) {
            expression = TABLE + "." + field.column().name();
        } else {
            expression = "(jsonb_path_query_array(" + field.jsonRoot() + ", "
                + params.bind(field.jsonPath()) + "::jsonpath) ->> 0)";
        }
        // A sort runs on the sub-field's own values, which the normalizer has folded, so a
        // lowercase name is not ordered after every uppercase one.
        return new Sort(field.normalised() ? normalised(expression) : expression,
            params.values(), ascending);
    }

    /**
     * Elasticsearch accepts _source as an array of includes, a single pattern, a boolean, or an
     * object with includes/excludes. CCD only ever sends the array form, which the jest to
     * elasticsearch converter rewrites into {@code {"includes": [...]}}.
     */
    private List<String> sourceIncludes(JsonNode node) {
        if (node == null || node.isNull() || node.isBoolean()) {
            return List.of();
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        if (node.isArray()) {
            var includes = new ArrayList<String>();
            node.forEach(element -> includes.add(element.asText()));
            return includes;
        }
        if (node.isObject()) {
            for (var name : fieldNames(node)) {
                if (!"includes".equals(name)) {
                    throw EsStubException.unsupported("_source." + name, "Only _source includes are supported.");
                }
            }
            return sourceIncludes(node.get("includes"));
        }
        throw EsStubException.badRequest("_source must be a string, array, boolean or object");
    }

    private List<JsonNode> clauses(JsonNode node, String name) {
        var clauses = new ArrayList<JsonNode>();
        if (node == null || node.isNull()) {
            return clauses;
        }
        if (node.isArray()) {
            node.forEach(clauses::add);
            return clauses;
        }
        if (node.isObject()) {
            clauses.add(node);
            return clauses;
        }
        throw EsStubException.badRequest("bool." + name + " must be a query clause or an array of them");
    }

    /**
     * The field a single field clause names. Elasticsearch's query builders serialise boost and
     * _name alongside the field name rather than inside its options, so those are skipped.
     */
    private Map.Entry<String, JsonNode> singleField(JsonNode node, String clause) {
        requireObject(node, clause);
        Map.Entry<String, JsonNode> found = null;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if ("boost".equals(entry.getKey()) || "_name".equals(entry.getKey())) {
                continue;
            }
            if (found != null) {
                throw EsStubException.badRequest("a " + clause + " clause must name exactly one field, got "
                    + fieldNames(node));
            }
            found = entry;
        }
        if (found == null) {
            throw EsStubException.badRequest("a " + clause + " clause must name a field");
        }
        return found;
    }

    private void requireObject(JsonNode node, String clause) {
        if (node == null || !node.isObject()) {
            throw EsStubException.badRequest("the '" + clause + "' clause must be a json object");
        }
    }

    private void rejectSlop(JsonNode spec, String clause) {
        if (spec.isObject()) {
            for (var name : fieldNames(spec)) {
                switch (name) {
                    case "query", "analyzer", "boost", "_name", "max_expansions", "zero_terms_query" -> { }
                    case "slop" -> {
                        if (spec.get(name).asInt() != 0) {
                            throw EsStubException.unsupported("slop", "Phrase matching is exact in the stub.");
                        }
                    }
                    default -> throw EsStubException.unsupported(name, "It appeared inside a " + clause + " query.");
                }
            }
        }
    }

    private String scalar(JsonNode node, String clause, Set<String> valueKeys) {
        if (node.isObject()) {
            for (var key : valueKeys) {
                if (node.has(key)) {
                    return text(node.get(key), clause);
                }
            }
            throw EsStubException.badRequest("a " + clause + " clause must carry one of " + valueKeys);
        }
        return text(node, clause);
    }

    private String text(JsonNode node, String clause) {
        if (node == null || node.isNull()) {
            throw EsStubException.badRequest("a " + clause + " clause carried a null value");
        }
        if (node.isContainerNode()) {
            throw EsStubException.badRequest("a " + clause + " clause carried " + node.getNodeType()
                + " where a value was expected");
        }
        return node.asText();
    }

    private int intValue(JsonNode node, int defaultValue, String name) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.canConvertToInt() || node.asInt() < 0) {
            throw EsStubException.badRequest(name + " must be a non-negative whole number");
        }
        return node.asInt();
    }

    private boolean booleanValue(JsonNode node, boolean defaultValue) {
        return node == null || node.isNull() ? defaultValue : node.asBoolean(defaultValue);
    }

    private List<String> fieldNames(JsonNode node) {
        var names = new ArrayList<String>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }

    static List<String> tokenise(String value) {
        var tokens = new ArrayList<String>();
        var analysed = squashJoiners(value.toLowerCase(Locale.ROOT), WORD_JOINER_JAVA_REGEX);
        for (var token : analysed.split(TOKEN_SEPARATOR_JAVA_REGEX)) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String squashJoiners(String value, String regex) {
        return value.replaceAll(regex, "$1$2");
    }

    /**
     * Folds a value the way {@code lowercase_normalizer} does, using the same character pairs as
     * the postgres {@code translate} it is compared against.
     */
    static String normalise(String value) {
        var folded = new StringBuilder(value.length());
        for (var c : value.toCharArray()) {
            var accent = ACCENTED.indexOf(c);
            folded.append(accent < 0 ? c : UNACCENTED.charAt(accent));
        }
        return folded.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Translates an elasticsearch wildcard pattern to a SQL LIKE pattern, escaping the characters
     * LIKE treats specially so that they only match themselves.
     */
    static String wildcardToLike(String pattern) {
        var like = new StringBuilder();
        for (var c : pattern.toCharArray()) {
            switch (c) {
                case '*' -> like.append('%');
                case '?' -> like.append('_');
                case '%', '_', '\\' -> like.append('\\').append(c);
                default -> like.append(c);
            }
        }
        return like.toString();
    }

    private static String likeEscape(String value) {
        var escaped = new StringBuilder();
        for (var c : value.toCharArray()) {
            if (c == '%' || c == '_' || c == '\\') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
