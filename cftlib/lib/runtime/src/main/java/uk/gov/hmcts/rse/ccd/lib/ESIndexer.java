package uk.gov.hmcts.rse.ccd.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Map;
import java.util.Set;

// Simple indexer replicating logstash functionality
// but saving up to ~1GB of RAM.
@Component
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

        RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
                new HttpHost("localhost", 9200)));

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

                BulkRequest request = new BulkRequest();
                while (results.next()) {
                    var row = results.getString("row");
                    request.add(new IndexRequest(results.getString("index_id"))
                            .id(results.getString("id"))
                            .source(row, XContentType.JSON));

                    // Replicate CCD's globalsearch logstash setup.
                    // Where cases define a 'SearchCriteria' field we index certain fields into CCD's central
                    // 'global search' index.
                    // https://github.com/hmcts/cnp-flux-config/blob/master/apps/ccd/ccd-logstash/ccd-logstash.yaml#L99-L175
                    var mapper = new ObjectMapper();
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

                        request.add(new IndexRequest("global_search")
                                .id(results.getString("id"))
                                .source(mapper.writeValueAsString(map), XContentType.JSON));
                    }
                }
                if (request.numberOfActions() > 0) {
                    var r = client.bulk(request, RequestOptions.DEFAULT);
                    if (r.hasFailures()) {
                        throw  new RuntimeException("**** Cftlib elasticsearch indexing error(s): "
                                + r.buildFailureMessage());
                    }
                }
            }
        }
    }

    public void filter(Map<String, Object> map, String... forKeys) {
        if (null != map) {
            var keyset = Set.of(forKeys);
            map.keySet().removeIf(k -> !keyset.contains(k));
        }
    }
}

