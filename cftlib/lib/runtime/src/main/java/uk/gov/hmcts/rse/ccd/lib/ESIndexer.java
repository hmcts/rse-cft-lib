package uk.gov.hmcts.rse.ccd.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Simple indexer replicating logstash functionality
// but saving up to ~1GB of RAM.
@Component
@ConditionalOnProperty(value = "ccd.sdk.decentralised", havingValue = "false", matchIfMissing = true)
public class ESIndexer {

    @SneakyThrows
    @Autowired
    public ESIndexer() {
        var t = new Thread(this::index);
        t.setDaemon(true);
        t.setUncaughtExceptionHandler(ControlPlane.failFast);
        t.setName("****Cftlib ElasticSearch indexer");
        t.start();
    }

    @SneakyThrows
    private void index() {
        ControlPlane.waitForBoot();

        var client = HttpClient.newHttpClient();
        var mapper = new ObjectMapper();
        var ensuredIndices = new HashSet<String>();

        try (Connection c = ControlPlane.getApi().getConnection(Database.Datastore)) {
            while (true) {
                Thread.sleep(1000);

                // Replicates the behaviour of the previous logstash configuration.
                // https://github.com/hmcts/rse-cft-lib/blob/94aa0edeb0e1a4337a411ed8e6e20f170ed30bae/cftlib/lib/runtime/compose/logstash/logstash_conf.in#L3
                var results = c.prepareStatement("""
                          with updated as (
                            update case_data
                            set marked_by_logstash = true
                            where not marked_by_logstash
                            returning *
                          )
                          select id, case_type_id, index_id, row_to_json(row)::jsonb as row
                          from (select
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
                          from updated) row
                        """).executeQuery();

                var request = new StringBuilder();
                var actionCount = 0;
                while (results.next()) {
                    var row = results.getString("row");
                    var index = results.getString("index_id");
                    if (ensuredIndices.add(index)) {
                        ensureCaseIndex(client, index);
                    }
                    appendIndexRequest(request, index, results.getString("id"), row);
                    actionCount++;

                    // Replicate CCD's globalsearch logstash setup.
                    // Where cases define a 'SearchCriteria' field we index certain fields into CCD's central
                    // 'global search' index.
                    // https://github.com/hmcts/cnp-flux-config/blob/master/apps/ccd/ccd-logstash/ccd-logstash.yaml#L99-L175
                    Map<String, Object> map = mapper.readValue(row, Map.class);
                    var data = (Map<String, Object>) map.get("data");
                    if (data.containsKey("SearchCriteria")) {
                        filter(data, "SearchCriteria", "caseManagementLocation", "CaseAccessCategory",
                                "caseNameHmctsInternal", "caseManagementCategory");
                        filter((Map<String, Object>) map.get("supplementary_data"), "HMCTSServiceId");
                        filter((Map<String, Object>) map.get("data_classification"), "SearchCriteria",
                                "CaseAccessCategory", "caseManagementLocation", "caseNameHmctsInternal",
                                "caseManagementCategory");
                        map.remove("last_state_modified_date");
                        map.remove("last_modified");
                        map.remove("created_date");
                        map.put("index_id", "global_search");

                        appendIndexRequest(request, "global_search", results.getString("id"),
                            mapper.writeValueAsString(map));
                        actionCount++;
                    }
                }
                if (actionCount > 0) {
                    var bulkRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:9200/_bulk"))
                        .header("Content-Type", "application/x-ndjson")
                        .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                        .build();
                    var response = client.send(bulkRequest, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 300) {
                        throw new RuntimeException("**** Cftlib elasticsearch indexing error: HTTP "
                            + response.statusCode() + " " + response.body());
                    }
                    var responseBody = mapper.readValue(response.body(), Map.class);
                    if (Boolean.TRUE.equals(responseBody.get("errors"))) {
                        throw new RuntimeException("**** Cftlib elasticsearch indexing error(s): "
                            + response.body());
                    }
                }
            }
        }
    }

    private void ensureCaseIndex(HttpClient client, String index) throws Exception {
        var check = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:9200/" + index))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
        var checkResponse = client.send(check, HttpResponse.BodyHandlers.discarding());
        if (checkResponse.statusCode() == 200) {
            return;
        }
        if (checkResponse.statusCode() != 404) {
            throw new RuntimeException("**** Cftlib elasticsearch index check error: HTTP "
                + checkResponse.statusCode());
        }

        var create = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:9200/" + index))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""
                {
                  "settings": {
                    "index.number_of_shards": 1,
                    "index.number_of_replicas": 0,
                    "index.mapping.total_fields.limit": 10000
                  },
                  "mappings": {
                    "properties": {
                      "@timestamp": { "enabled": false },
                      "@version": { "enabled": false },
                      "id": { "type": "long" },
                      "reference": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                      "jurisdiction": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                      "case_type_id": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                      "state": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                      "security_classification": { "type": "keyword" },
                      "created_date": { "type": "date", "ignore_malformed": true },
                      "last_modified": { "type": "date", "ignore_malformed": true },
                      "last_state_modified_date": { "type": "date", "ignore_malformed": true },
                      "data": {
                        "type": "object",
                        "dynamic": true,
                        "properties": {
                          "CaseAccessCategory": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                          "caseManagementLocation": {
                            "properties": {
                              "baseLocation": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                              "region": { "type": "text", "fields": { "keyword": { "type": "keyword" } } }
                            }
                          },
                          "CaseAccessGroups": {
                            "properties": {
                              "value": {
                                "properties": {
                                  "caseAccessGroupId": {
                                    "type": "text",
                                    "fields": { "keyword": { "type": "keyword" } }
                                  }
                                }
                              }
                            }
                          }
                        }
                      },
                      "data_classification": { "type": "object", "enabled": false },
                      "supplementary_data": { "type": "object", "dynamic": true },
                      "index_id": { "enabled": false }
                    }
                  }
                }
                """))
            .build();
        var createResponse = client.send(create, HttpResponse.BodyHandlers.ofString());
        if (createResponse.statusCode() >= 300) {
            throw new RuntimeException("**** Cftlib elasticsearch index create error: HTTP "
                + createResponse.statusCode() + " " + createResponse.body());
        }
    }

    private void appendIndexRequest(StringBuilder request, String index, String id, String source) {
        request.append("{\"index\":{\"_index\":\"")
            .append(index)
            .append("\",\"_id\":\"")
            .append(id)
            .append("\"}}\n")
            .append(source)
            .append('\n');
    }

    public void filter(Map<String, Object> map, String... forKeys) {
        if (null != map) {
            var keyset = Set.of(forKeys);
            map.keySet().removeIf(k -> !keyset.contains(k));
        }
    }
}
