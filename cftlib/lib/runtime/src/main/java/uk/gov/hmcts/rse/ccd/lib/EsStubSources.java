package uk.gov.hmcts.rse.ccd.lib;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The databases the elasticsearch stub reads case data from, and the SQL that reproduces the
 * document each indexer would have written.
 *
 * <p>Sources default to the data store database plus any the consuming build asked cftlib to
 * create, and can be overridden with RSE_LIB_ES_STUB_SOURCES, a comma separated list of
 * {@code database[:schema]}. Whether a source holds centralised or decentralised case data is
 * detected from its catalog rather than configured: the decentralised schema carries
 * {@code case_event.case_revision}. A database with no case data is simply not a source, and
 * because migrations run while the stub is already serving, that and the shape it reports are
 * re-checked periodically rather than settled once.
 */
class EsStubSources {

    static final String GLOBAL_SEARCH_INDEX = "global_search";
    private static final String CASE_INDEX_SUFFIX = "_cases";

    /** Identifiers are interpolated into SQL, so only these may be configured. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,62}");

    /** The case data keys the global search document keeps, from the logstash configuration. */
    private static final List<String> GLOBAL_SEARCH_DATA_KEYS = List.of(
        "SearchCriteria", "caseManagementLocation", "CaseAccessCategory", "caseNameHmctsInternal",
        "caseManagementCategory");

    /** A configured database, once probed. */
    record Source(String database, String schema, boolean decentralised) {
    }

    /** Opens a connection to one of the configured databases. */
    interface Connections {
        Connection open(String database) throws SQLException;
    }

    private final Map<String, Source> probed = new LinkedHashMap<>();
    private final Map<String, Long> probedUntil = new LinkedHashMap<>();
    private static final long PROBE_TTL_MILLIS = 2_000;
    private final Set<String> caseTypeOwners = ConcurrentHashMap.newKeySet();
    private final List<String> configured;
    private final Connections connections;
    private final Set<String> optional;

    EsStubSources() {
        this(ControlPlane.getEnvVar("RSE_LIB_ES_STUB_SOURCES", defaultSources()), EsStubSources::cftlibConnection,
            System.getenv("RSE_LIB_ES_STUB_SOURCES") == null ? discovered() : Set.of());
    }

    /**
     * The data store plus any database the consuming build asked cftlib to create. A decentralised
     * service keeps its case data in one of those, and searching only the data store would answer
     * from its metadata rows, whose case data is empty. Databases that turn out to hold no case
     * data are dropped when they are probed.
     */
    private static String defaultSources() {
        var sources = new ArrayList<String>();
        sources.add(Database.Datastore.toString().toLowerCase(Locale.ROOT));
        sources.addAll(discovered());
        return String.join(",", sources);
    }

    private static Set<String> discovered() {
        var databases = new LinkedHashSet<String>();
        var additional = System.getenv("RSE_LIB_ADDITIONAL_DATABASES");
        if (additional != null) {
            for (var database : additional.split(",")) {
                if (!database.isBlank()) {
                    databases.add(database.trim());
                }
            }
        }
        return databases;
    }

    EsStubSources(String configuration, Connections connections) {
        this(configuration, connections, Set.of());
    }

    EsStubSources(String configuration, Connections connections, Set<String> optional) {
        this.connections = connections;
        this.optional = optional;
        configured = new ArrayList<>();
        for (var entry : configuration.split(",")) {
            if (!entry.isBlank()) {
                configured.add(entry.trim());
            }
        }
        if (configured.isEmpty()) {
            throw new IllegalArgumentException(
                "RSE_LIB_ES_STUB_SOURCES names no databases. Expected database[:schema] entries.");
        }
    }

    List<String> configured() {
        return configured;
    }

    /**
     * The sources that may hold documents for an index. Case indices live in whichever source
     * defines the case type; the global search index spans every source.
     */
    List<Source> sourcesFor(String index) throws SQLException {
        var sources = new ArrayList<Source>();
        for (var entry : configured) {
            var source = source(entry);
            if (source != null) {
                sources.add(source);
            }
        }
        if (GLOBAL_SEARCH_INDEX.equals(index)) {
            // Global search spans case types and so every source, including the data store for
            // centralised ones. A decentralised service's data store row cannot contribute a
            // duplicate because the global search views only project cases whose data carries
            // SearchCriteria, and that row holds no data.
            return sources;
        }
        var caseTypeId = caseTypeOf(index);
        var owning = new ArrayList<Source>();
        for (var source : sources) {
            if (holdsCaseType(source, caseTypeId)) {
                owning.add(source);
            }
        }

        // A decentralised service's case data lives in its own database, while the data store keeps
        // a metadata-only row for the same case. Only the service's data is ever indexed, because
        // the centralised indexer is off for those case types, so the data store row must not
        // shadow it here either.
        var decentralised = owning.stream().filter(Source::decentralised).toList();
        return decentralised.isEmpty() ? owning : decentralised;
    }

    /**
     * The case type an index name addresses, mirroring the data store's cases index name format.
     */
    static String caseTypeOf(String index) {
        if (!index.endsWith(CASE_INDEX_SUFFIX) || index.length() == CASE_INDEX_SUFFIX.length()) {
            throw EsStubException.badRequest("'" + index + "' is not an index the stub serves. It serves '"
                + GLOBAL_SEARCH_INDEX + "' and '<case type>" + CASE_INDEX_SUFFIX + "'.");
        }
        return index.substring(0, index.length() - CASE_INDEX_SUFFIX.length());
    }

    Connection connect(Source source) throws SQLException {
        return connections.open(source.database());
    }

    /**
     * Parameters are sent untyped so that postgres infers each one from the comparison it appears
     * in, which keeps the translated SQL free of casts for columns such as the
     * security_classification enum.
     */
    private static Connection cftlibConnection(String database) throws SQLException {
        return DriverManager.getConnection(
            "jdbc:postgresql://" + CftlibConfig.dbHost() + ":" + CftlibConfig.dbPort() + "/" + database
                + "?stringtype=unspecified",
            CftlibConfig.dbUsername(), CftlibConfig.dbPassword());
    }

    /**
     * Resolves a configured entry, remembering the answer only briefly.
     *
     * <p>A verdict reached while the service's migrations are still running can be wrong — the
     * decentralised schema gains {@code case_data} several migrations before
     * {@code case_event.case_revision} — and a database that holds no case data yet may hold some
     * later. Remembering either for the run would answer from the wrong shape, or not at all, for
     * as long as it lasted; re-probing on every search would instead open a connection per search
     * inside this lock. A short expiry bounds both.
     */
    private synchronized Source source(String entry) throws SQLException {
        var cached = probed.get(entry);
        if (probed.containsKey(entry) && System.currentTimeMillis() <= probedUntil.get(entry)) {
            return cached;
        }
        var parts = entry.split(":", 2);
        var database = identifier(parts[0], entry);
        var schema = parts.length > 1 ? identifier(parts[1], entry) : null;

        try (var c = connections.open(database)) {
            var resolved = probe(c, database, schema);
            if (cached == null || cached.decentralised() != resolved.decentralised()) {
                System.out.println("**** Cftlib elasticsearch stub reading " + (resolved.decentralised()
                    ? "decentralised" : "centralised") + " case data from " + database + "." + resolved.schema());
            }
            remember(entry, resolved);
            return resolved;
        } catch (IllegalStateException e) {
            // A database cftlib discovered rather than was told about may hold no case data at
            // all, which is not a misconfiguration to report. Remembered, because it is a fact
            // about that database rather than about this moment.
            if (!optional.contains(entry)) {
                throw e;
            }
            remember(entry, null);
            return null;
        } catch (SQLException e) {
            // Not remembered: a database that could not be reached just now may well answer the
            // next search, and holding on to the failure would answer that one from the other
            // sources alone, with the decentralised cases carrying no data at all.
            if (!optional.contains(entry)) {
                throw e;
            }
            return null;
        }
    }


    private void remember(String entry, Source source) {
        probed.put(entry, source);
        probedUntil.put(entry, System.currentTimeMillis() + PROBE_TTL_MILLIS);
    }

    private Source probe(Connection c, String database, String requestedSchema) throws SQLException {
        var schema = requestedSchema;
        if (schema == null) {
            var candidates = new ArrayList<String>();
            try (var s = c.prepareStatement("""
                select table_schema
                from information_schema.tables
                where table_name = 'case_data'
                  and table_schema not in ('pg_catalog', 'information_schema')
                order by table_schema
                """); var rs = s.executeQuery()) {
                while (rs.next()) {
                    candidates.add(rs.getString(1));
                }
            }
            if (candidates.isEmpty()) {
                throw new IllegalStateException("The elasticsearch stub found no case_data table in database '"
                    + database + "'. Check RSE_LIB_ES_STUB_SOURCES.");
            }
            // ccd ahead of public: a decentralised service's own case data lives in ccd, which is
            // what its indexer reads, and a database carrying both would otherwise be read from
            // the wrong one. The data store has only public.
            schema = candidates.contains("ccd") ? "ccd"
                : candidates.contains("public") ? "public" : candidates.get(0);
        }

        boolean decentralised;
        try (var s = c.prepareStatement("""
            select count(*)
            from information_schema.columns
            where table_schema = ?
              and table_name = 'case_event'
              and column_name = 'case_revision'
            """)) {
            s.setString(1, schema);
            try (var rs = s.executeQuery()) {
                rs.next();
                decentralised = rs.getInt(1) > 0;
            }
        }
        return new Source(database, schema, decentralised);
    }

    private boolean holdsCaseType(Source source, String caseTypeId) throws SQLException {
        // Only hits are remembered. A case type has no rows until its first case is created, and
        // callers poll a search until one appears, so a miss must stay a miss.
        if (caseTypeOwners.contains(source.database() + "." + caseTypeId)) {
            return true;
        }
        if (probeCaseType(source, caseTypeId)) {
            caseTypeOwners.add(source.database() + "." + caseTypeId);
            return true;
        }
        return false;
    }

    private boolean probeCaseType(Source source, String caseTypeId) throws SQLException {
        try (var c = connect(source);
             var s = c.prepareStatement("select 1 from " + qualified(source, "case_data")
                 + " where lower(case_type_id) = ? limit 1")) {
            s.setString(1, caseTypeId.toLowerCase(Locale.ROOT));
            try (var rs = s.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * The document view for an index: every row the matching indexer would have written, with the
     * document itself alongside the columns the query translator filters and sorts on.
     *
     * <p>The inner select lists are lifted from the two indexers so that the document's field
     * names line up with the real index by construction.
     */
    String documentView(Source source, String index) {
        return view(source, index, true);
    }

    /**
     * The same view without the document column, for counting: building a json document per row
     * is the expensive part, and a count needs none of them.
     */
    String countView(Source source, String index) {
        return view(source, index, false);
    }

    private String view(Source source, String index, boolean withDocument) {
        var caseIndex = source.decentralised()
            ? decentralisedCaseRows(source)
            : centralisedCaseRows(source);

        if (GLOBAL_SEARCH_INDEX.equals(index)) {
            return source.decentralised()
                ? projection(decentralisedGlobalSearchRows(caseIndex), false, false, withDocument)
                : projection(centralisedGlobalSearchRows(caseIndex), true, false, withDocument);
        }
        return projection(caseIndex, !source.decentralised(), true, withDocument);
    }

    /**
     * Projects an indexer's row set onto the uniform column set the query translator targets,
     * plus the document the indexer would have produced.
     */
    private String projection(String rows, boolean hasDataClassification, boolean hasDates,
                              boolean withDocument) {
        var columns = new ArrayList<String>(List.of(
            "r.id as id",
            "r.reference as reference",
            "r.state as state",
            "r.jurisdiction as jurisdiction",
            "r.case_type_id as case_type_id",
            // The data store maps this to an enum type, so compare it as text.
            "r.security_classification::text as security_classification",
            "r.data as data",
            "r.supplementary_data as supplementary_data",
            "r.index_id as index_id"));
        columns.add((hasDataClassification ? "r.data_classification" : "null::jsonb") + " as data_classification");
        for (var date : List.of("created_date", "last_modified", "last_state_modified_date")) {
            columns.add((hasDates ? "r." + date : "null::timestamp") + " as " + date);
        }
        if (withDocument) {
            columns.add("row_to_json(r)::jsonb as document");
        }

        return "select\n    " + String.join(",\n    ", columns) + "\nfrom (\n" + rows + ") r\n";
    }

    /**
     * The centralised indexer's row set, from ESIndexer.
     */
    private String centralisedCaseRows(Source source) {
        return """
                select
                    now() as "@timestamp",
                    version::text as "@version",
                    case_type_id,
                    created_date,
                    data,
                    data_classification,
                    id,
                    jurisdiction,
                    reference,
                    last_modified,
                    last_state_modified_date,
                    supplementary_data,
                    lower(case_type_id) || '_cases' as index_id,
                    state,
                    security_classification
                from %s
            """.formatted(qualified(source, "case_data"));
    }

    /**
     * The decentralised indexer's row set, from the SDK's DecentralisedESIndexer. The queue the
     * indexer claims from is replaced by the latest revision of each case, since the stub reads
     * the source of truth rather than following the queue.
     */
    private String decentralisedCaseRows(Source source) {
        return """
                select
                    cd.reference,
                    ce.case_revision,
                    cd.case_type_id,
                    lower(cd.case_type_id) || '_cases' as index_id,
                    cd.created_date,
                    cd.jurisdiction,
                    cd.id as id,
                    cd.state,
                    cd.security_classification,
                    cd.last_state_modified_date,
                    cd.supplementary_data,
                    ce.id as event_id,
                    ce.data,
                    coalesce(cd.last_modified, cd.created_date) as last_modified
                from %s cd
                join lateral (
                    select ce.*
                    from %s ce
                    where ce.case_data_id = cd.id
                    order by ce.case_revision desc, ce.id desc
                    limit 1
                ) ce on true
            """.formatted(qualified(source, "case_data"), qualified(source, "case_event"));
    }

    /**
     * The global search document the centralised indexer writes for cases that define
     * SearchCriteria: the case document with its data, data classification and supplementary data
     * filtered, and the dates removed.
     */
    private String centralisedGlobalSearchRows(String caseRows) {
        return """
                select
                    base."@timestamp" as "@timestamp",
                    base."@version" as "@version",
                    base.case_type_id,
                    %s as data,
                    %s as data_classification,
                    base.id,
                    base.jurisdiction,
                    base.reference,
                    %s as supplementary_data,
                    'global_search'::text as index_id,
                    base.state,
                    base.security_classification
                from (
                %s
                ) base
                where jsonb_exists(base.data, 'SearchCriteria')
            """.formatted(
                filtered("base.data", GLOBAL_SEARCH_DATA_KEYS),
                filtered("base.data_classification", GLOBAL_SEARCH_DATA_KEYS),
                filtered("base.supplementary_data", List.of("HMCTSServiceId")),
                caseRows);
    }

    /**
     * The decentralised equivalent. The decentralised document carries no data classification.
     */
    private String decentralisedGlobalSearchRows(String caseRows) {
        return """
                select
                    base.reference,
                    base.case_revision,
                    base.case_type_id,
                    'global_search'::text as index_id,
                    base.jurisdiction,
                    base.id,
                    base.state,
                    base.security_classification,
                    %s as supplementary_data,
                    base.event_id,
                    %s as data
                from (
                %s
                ) base
                where jsonb_exists(base.data, 'SearchCriteria')
            """.formatted(
                filtered("base.supplementary_data", List.of("HMCTSServiceId")),
                filtered("base.data", GLOBAL_SEARCH_DATA_KEYS),
                caseRows);
    }

    /**
     * Keeps only the named keys of a jsonb object, as the indexers' filter method does. A null
     * object stays null; an object that loses every key becomes empty.
     */
    private String filtered(String column, List<String> keys) {
        var quoted = new ArrayList<String>();
        for (var key : keys) {
            quoted.add("'" + key.replace("'", "''") + "'");
        }
        return "case when " + column + " is null then null else coalesce((select jsonb_object_agg(e.key, e.value) "
            + "from jsonb_each(" + column + ") e where e.key in (" + String.join(", ", quoted) + ")), '{}'::jsonb) end";
    }

    private String qualified(Source source, String table) {
        return "\"" + source.schema() + "\".\"" + table + "\"";
    }

    private static String identifier(String value, String entry) {
        var trimmed = value.trim();
        if (!IDENTIFIER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("RSE_LIB_ES_STUB_SOURCES entry '" + entry
                + "' is not a valid database[:schema] name.");
        }
        return trimmed;
    }
}
