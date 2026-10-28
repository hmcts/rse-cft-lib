package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves the subset of the elasticsearch API that CCD uses, querying case data from postgres
 * instead of maintaining an index.
 *
 * <p>Because the stub reads the case tables directly there is no indexing lag and no index to
 * create, so the indexers are switched off in this mode. The cost is fidelity: the stub answers
 * only the queries CCD generates, and rejects anything else rather than quietly returning no hits.
 */
public class EsStub implements ManagedDependency {

    private static final String PRODUCT_HEADER = "X-Elastic-Product";
    private static final int QUERY_THREADS = 8;

    private final ObjectMapper mapper = new ObjectMapper();
    private final EsStubSources sources;
    private HttpServer server;
    private ExecutorService requests;

    public EsStub() {
        this(new EsStubSources());
    }

    EsStub(EsStubSources sources) {
        this.sources = sources;
    }

    @Override
    public String name() {
        return "elasticsearch-stub";
    }

    @Override
    public void start() throws IOException {
        var url = URI.create(CftlibConfig.esUrl());
        start(url.getHost() == null ? "localhost" : url.getHost(), url.getPort() == -1 ? 9200 : url.getPort());
    }

    void start(String host, int port) throws IOException {
        if (Ports.isListening(host, port)) {
            // Adopting would be worse than failing: the stub answers from the case tables, so
            // cftlib runs no indexer in this mode and an elasticsearch it did not start stays
            // empty however long anything waits for a document to appear in it.
            throw new IOException("Port " + port + " on " + host + " is already in use, so the cftlib "
                + "elasticsearch stub cannot serve it. A leftover cftlib container is the usual cause, "
                + "and 'docker rm -f cftlib-ccd-elasticsearch-1' clears it. To use an elasticsearch that "
                + "is already running, set RSE_LIB_ES=external instead, which keeps cftlib's indexers on "
                + "so that it gets populated.");
        }
        bind(host, port);
    }

    void bind(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        requests = Executors.newFixedThreadPool(QUERY_THREADS);
        server.setExecutor(requests);
        server.createContext("/", this::handle);
        server.start();
        System.out.println("**** Cftlib elasticsearch stub listening on " + host + ":" + port()
            + ", answering from " + sources.configured());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (requests != null) {
            // HttpServer shuts down only an executor it created itself, and these threads are
            // not daemons, so they would outlive a stop that is meant to release everything.
            requests.shutdownNow();
        }
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/".equals(path)) {
                respond(exchange, 200, info());
            } else if ("GET".equals(method) && "/_cat/health".equals(path)) {
                health(exchange);
            } else if ("POST".equals(method) && path.endsWith("/_msearch")) {
                respond(exchange, 200, msearch(body(exchange), indexOf(path, "/_msearch")));
            } else if ("POST".equals(method) && path.endsWith("/_search")) {
                search(exchange, indexOf(path, "/_search"));
            } else if ("GET".equals(method) && path.contains("/_doc/")) {
                var parts = path.substring(1).split("/_doc/", 2);
                if (parts.length != 2) {
                    throw EsStubException.badRequest("a document fetch must name an index, as in "
                        + "'/{index}/_doc/{id}'");
                }
                document(exchange, parts[0], parts[1]);
            } else {
                // The status in the body has to agree with the transport's: elasticsearch clients
                // read the envelope's status for multi search items.
                respond(exchange, 404, error(new EsStubException(EsStubException.BAD_REQUEST, 404,
                    method + " " + path + " is not served. The cftlib elasticsearch stub answers only '/', "
                        + "'/_cat/health', '/{index}/_doc/{id}', '/{index}/_search' and '/_msearch'.")));
            }
        } catch (EsStubException e) {
            reportFailure(exchange, e.status(), e);
        } catch (Exception e) {
            reportFailure(exchange, 500, new EsStubException(EsStubException.QUERY_ERROR, 500,
                "The cftlib elasticsearch stub failed: " + e));
        } finally {
            exchange.close();
        }
    }

    /**
     * Reports a failure, tolerating a response that has already begun: a caller that disconnects
     * mid-write leaves the headers sent, and trying to send them again would replace the real
     * failure with a complaint about the attempt.
     */
    private void reportFailure(HttpExchange exchange, int status, EsStubException failure) {
        try {
            respond(exchange, status, error(failure));
        } catch (IOException | IllegalStateException e) {
            System.out.println("**** Cftlib elasticsearch stub could not report a failure to its caller: "
                + failure.getMessage());
        }
    }

    private void search(HttpExchange exchange, String index) throws IOException, SQLException {
        if (index == null) {
            throw EsStubException.badRequest("a search must name an index");
        }
        respond(exchange, 200, execute(single(index), parse(body(exchange))));
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw EsStubException.badRequest("the search body was not json: " + e.getMessage());
        }
    }

    /**
     * Answers an ndjson multi search, whose lines alternate between a header naming the index and
     * a search body. A failing search becomes an error entry in its own slot, as elasticsearch
     * does, so that the caller reports which search failed.
     */
    private ObjectNode msearch(String body, String pathIndex) throws SQLException {
        // Paired as text and parsed inside each slot below: a line that is not json belongs to
        // one search, and parsing here would deny the searches beside it their results.
        var requests = new ArrayList<String[]>();
        String header = null;
        for (var line : body.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (header == null) {
                header = line;
            } else {
                requests.add(new String[] {header, line});
                header = null;
            }
        }
        if (header != null) {
            throw EsStubException.badRequest("a multi search ended with a header and no search body");
        }

        var responses = mapper.createArrayNode();
        for (var request : requests) {
            try {
                // Inside the slot: a header or body this stub will not serve must fail its own
                // search rather than the valid ones alongside it.
                var index = headerIndex(parse(request[0]), pathIndex);
                if (index == null) {
                    throw EsStubException.badRequest("a multi search header named no index");
                }
                var result = execute(index, parse(request[1]));
                result.put("status", 200);
                responses.add(result);
            } catch (EsStubException e) {
                responses.add(error(e));
            }
        }
        var response = mapper.createObjectNode();
        // co.elastic.clients requires took at both the multi search and the individual search
        // level, and fails the whole response without it.
        response.put("took", 1);
        response.set("responses", responses);
        return response;
    }

    private String headerIndex(JsonNode header, String fallback) {
        if (header != null && header.isObject() && header.has("index")) {
            var index = header.get("index");
            if (index.isArray() && !index.isEmpty()) {
                // Answering from the first alone would drop the rest and report 200 with no hits.
                if (index.size() > 1) {
                    throw EsStubException.unsupported("a search over several indices",
                        "CCD searches one index per multi search line.");
                }
                return single(index.get(0).asText());
            }
            if (index.isTextual()) {
                return single(index.asText());
            }
        }
        return fallback == null ? null : single(fallback);
    }

    private String single(String index) {
        if (index.contains(",")) {
            throw EsStubException.unsupported("a search over several indices",
                "'" + index + "' names more than one, and CCD searches one index per request.");
        }
        return index;
    }

    /**
     * Fetches one indexed document by id, which consumers use to assert on what indexing
     * produced rather than to search.
     */
    private void document(HttpExchange exchange, String index, String id) throws IOException, SQLException {
        for (var source : sources.sourcesFor(index)) {
            try (var c = sources.connect(source);
                 var s = c.prepareStatement("select u.document from (\n"
                     + sources.documentView(source, index)
                     + ") u where u.index_id = ? and u.id::text = ?")) {
                s.setString(1, index);
                s.setString(2, id);
                try (var rs = s.executeQuery()) {
                    if (rs.next()) {
                        var found = mapper.createObjectNode();
                        found.put("_index", index);
                        found.put("_id", id);
                        found.put("found", true);
                        found.set("_source", mapper.readTree(rs.getString("document")));
                        respond(exchange, 200, found);
                        return;
                    }
                }
            }
        }
        var missing = mapper.createObjectNode();
        missing.put("_index", index);
        missing.put("_id", id);
        missing.put("found", false);
        respond(exchange, 404, missing);
    }

    private Connection connect(EsStubSources.Source source) {
        try {
            return sources.connect(source);
        } catch (SQLException e) {
            throw new EsStubException(EsStubException.QUERY_ERROR, 500,
                "The cftlib elasticsearch stub could not reach " + source.database() + ": " + e.getMessage());
        }
    }

    private ObjectNode execute(String index, JsonNode body) throws SQLException {
        var translator = new EsQueryTranslator(mapper);
        var search = translator.translate(body);
        List<EsStubSources.Source> matching;
        try {
            matching = sources.sourcesFor(index);
        } catch (SQLException e) {
            // Reported against this search rather than thrown, so that one unreachable database
            // does not fail every search in a multi search.
            throw new EsStubException(EsStubException.QUERY_ERROR, 500,
                "The cftlib elasticsearch stub could not reach the databases holding case data: " + e.getMessage());
        }

        var hits = mapper.createArrayNode();
        long total = 0;
        var wanted = search.from() + search.size();
        var answering = 0;
        for (var source : matching) {
            try (var c = connect(source)) {
                var before = hits.size();
                if (wanted > 0) {
                    collect(c, source, index, search, wanted, hits);
                }
                var returned = hits.size() - before;
                if (returned > 0) {
                    answering++;
                }
                // The page is the count whenever it came back short, which is the ordinary case
                // at development volumes. Only a full page can have more behind it, and only
                // then is a second scan of the case tables worth doing.
                total += returned < wanted ? returned : count(c, source, index, search);
            }
        }
        if (answering > 1) {
            System.out.println("**** Cftlib elasticsearch stub merged '" + index + "' across " + answering
                + " databases; results are ordered within each database but not interleaved across them.");
        }

        return response(total, page(hits, search));
    }

    private ArrayNode page(ArrayNode hits, EsQueryTranslator.Search search) {
        var page = mapper.createArrayNode();
        for (var i = search.from(); i < hits.size() && page.size() < search.size(); i++) {
            page.add(hits.get(i));
        }
        return page;
    }

    /**
     * Appends this source's hits, up to the page being returned.
     */
    private void collect(Connection c, EsStubSources.Source source, String index,
                         EsQueryTranslator.Search search, int limit, ArrayNode hits) {
        var order = new ArrayList<String>();
        for (var sort : search.sorts()) {
            order.add(sort.expression() + (sort.ascending() ? " asc" : " desc") + " nulls last");
        }
        var sql = "select u.id, u.document from (\n"
            + sources.documentView(source, index)
            + ") u where u.index_id = ? and (" + search.where() + ")"
            + (order.isEmpty() ? "" : " order by " + String.join(", ", order))
            + " limit " + limit;

        try (var s = c.prepareStatement(sql)) {
            var parameter = 1;
            s.setString(parameter++, index);
            for (var value : search.whereParams()) {
                s.setObject(parameter++, value);
            }
            for (var sort : search.sorts()) {
                for (var value : sort.params()) {
                    s.setObject(parameter++, value);
                }
            }
            try (var rs = s.executeQuery()) {
                while (rs.next()) {
                    hits.add(hit(index, rs.getString("id"), rs.getString("document"), search));
                }
            }
        } catch (SQLException e) {
            throw queryError(e, sql);
        }
    }

    /**
     * Counts this source's matches over a view without the document column, so that a page of
     * results does not cost a json document per matching case.
     */
    private long count(Connection c, EsStubSources.Source source, String index,
                       EsQueryTranslator.Search search) {
        var sql = "select count(*) from (\n" + sources.countView(source, index)
            + ") u where u.index_id = ? and (" + search.where() + ")";
        try (var s = c.prepareStatement(sql)) {
            var parameter = 1;
            s.setString(parameter++, index);
            for (var value : search.whereParams()) {
                s.setObject(parameter++, value);
            }
            try (var rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw queryError(e, sql);
        }
    }

    private EsStubException queryError(SQLException e, String sql) {
        System.out.println("**** Cftlib elasticsearch stub query failed: " + e.getMessage() + "\n" + sql);
        return new EsStubException(EsStubException.QUERY_ERROR, 400,
            "The cftlib elasticsearch stub could not run the translated search: " + e.getMessage());
    }

    private ObjectNode hit(String index, String id, String document, EsQueryTranslator.Search search) {
        try {
            var node = mapper.createObjectNode();
            node.put("_index", index);
            node.put("_id", id);
            node.put("_score", 1.0);
            if (search.sourceEnabled()) {
                node.set("_source", EsSourceFilter.apply(mapper.readTree(document), search.sourceIncludes()));
            }
            return node;
        } catch (IOException e) {
            throw new EsStubException(EsStubException.QUERY_ERROR, 500,
                "The cftlib elasticsearch stub could not read a case document: " + e.getMessage());
        }
    }

    private ObjectNode response(long total, ArrayNode hits) {
        var shards = mapper.createObjectNode();
        shards.put("total", 1);
        shards.put("successful", 1);
        shards.put("skipped", 0);
        shards.put("failed", 0);

        var totals = mapper.createObjectNode();
        totals.put("value", total);
        totals.put("relation", "eq");

        var hitsNode = mapper.createObjectNode();
        hitsNode.set("total", totals);
        hitsNode.put("max_score", hits.isEmpty() ? null : 1.0);
        hitsNode.set("hits", hits);

        var response = mapper.createObjectNode();
        response.put("took", 1);
        response.put("timed_out", false);
        response.set("_shards", shards);
        response.set("hits", hitsNode);
        return response;
    }

    private ObjectNode info() {
        var version = mapper.createObjectNode();
        version.put("number", "9.0.0");
        version.put("build_flavor", "default");
        version.put("build_type", "cftlib-stub");
        version.put("build_hash", "cftlib");
        version.put("build_snapshot", false);
        version.put("lucene_version", "10.0.0");
        version.put("minimum_wire_compatibility_version", "8.0.0");
        version.put("minimum_index_compatibility_version", "8.0.0");

        var info = mapper.createObjectNode();
        info.put("name", "cftlib-elasticsearch-stub");
        info.put("cluster_name", "cftlib");
        info.put("cluster_uuid", "cftlib-elasticsearch-stub");
        info.set("version", version);
        info.put("tagline", "Answering CCD's searches from postgres");
        return info;
    }

    private void health(HttpExchange exchange) throws IOException {
        var query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("format=json")) {
            var entry = mapper.createObjectNode();
            entry.put("cluster", "cftlib");
            entry.put("status", "green");
            respond(exchange, 200, mapper.createArrayNode().add(entry));
            return;
        }
        var body = (System.currentTimeMillis() / 1000) + " 00:00:00 cftlib green 1 1 0 0 0 0 0 0 - 100.0%\n";
        write(exchange, 200, "text/plain; charset=utf-8", body);
    }

    private ObjectNode error(EsStubException e) {
        var cause = mapper.createObjectNode();
        cause.put("type", e.type());
        cause.put("reason", e.getMessage());

        var error = mapper.createObjectNode();
        error.set("root_cause", mapper.createArrayNode().add(cause.deepCopy()));
        error.put("type", e.type());
        error.put("reason", e.getMessage());

        var body = mapper.createObjectNode();
        body.set("error", error);
        body.put("status", e.status());
        return body;
    }

    private String indexOf(String path, String suffix) {
        var index = path.substring(0, path.length() - suffix.length());
        while (index.startsWith("/")) {
            index = index.substring(1);
        }
        return index.isEmpty() ? null : index;
    }

    private String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, JsonNode body) throws IOException {
        write(exchange, status, "application/json; charset=utf-8", mapper.writeValueAsString(body));
    }

    private void write(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        // The elasticsearch clients refuse a response that does not identify the product.
        exchange.getResponseHeaders().set(PRODUCT_HEADER, "Elasticsearch");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
