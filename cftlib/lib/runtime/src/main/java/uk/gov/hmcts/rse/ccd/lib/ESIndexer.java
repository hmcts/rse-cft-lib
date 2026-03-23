package uk.gov.hmcts.rse.ccd.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Map;
import java.util.Set;

// Simple indexer replicating logstash functionality
// but saving up to ~1GB of RAM.
@Component
@ConditionalOnProperty(value = "ccd.sdk.decentralised", havingValue = "false", matchIfMissing = true)
public class ESIndexer {

    private final ObjectMapper mapper = new ObjectMapper();

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

        // Use the low-level RestClient to avoid RestHighLevelClient response-parsing
        // incompatibilities with Elasticsearch 8+.
        RestClient client = RestClient.builder(new HttpHost("localhost", 9200)).build();

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

                // Build NDJSON bulk request body directly, bypassing the high-level client
                // which cannot parse responses from Elasticsearch 8+.
                var bulkBody = new StringBuilder();
                int actionCount = 0;

                while (results.next()) {
                    var row = results.getString("row");
                    var indexId = results.getString("index_id");
                    var id = results.getString("id");

                    appendBulkIndex(bulkBody, indexId, id, row);
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

                        appendBulkIndex(bulkBody, "global_search", id, mapper.writeValueAsString(map));
                        actionCount++;
                    }
                }

                if (actionCount > 0) {
                    var request = new Request("POST", "/_bulk");
                    request.addParameter("timeout", "1m");
                    request.setEntity(new StringEntity(
                            bulkBody.toString(),
                            ContentType.create("application/x-ndjson", "UTF-8")));

                    var response = client.performRequest(request);
                    var statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode != 200) {
                        throw new RuntimeException(
                                "**** Cftlib elasticsearch bulk indexing failed with HTTP status: " + statusCode);
                    }
                    var responseBody = EntityUtils.toString(response.getEntity());
                    var responseMap = mapper.readValue(responseBody, Map.class);
                    if (Boolean.TRUE.equals(responseMap.get("errors"))) {
                        throw new RuntimeException(
                                "**** Cftlib elasticsearch indexing error(s): " + responseBody);
                    }
                }
            }
        }
    }

    private void appendBulkIndex(StringBuilder bulk, String index, String id, String source) {
        bulk.append("{\"index\":{\"_index\":\"").append(index)
            .append("\",\"_id\":\"").append(id).append("\"}}").append('\n');
        bulk.append(source).append('\n');
    }

    public void filter(Map<String, Object> map, String... forKeys) {
        if (null != map) {
            var keyset = Set.of(forKeys);
            map.keySet().removeIf(k -> !keyset.contains(k));
        }
    }
}

