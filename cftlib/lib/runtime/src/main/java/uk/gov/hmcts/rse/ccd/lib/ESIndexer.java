package uk.gov.hmcts.rse.ccd.lib;

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

@Component
public class ESIndexer {

    @SneakyThrows
    @Autowired
    public ESIndexer() {
        var t = new Thread(this::index);
        t.setDaemon(true);
        t.start();
    }

    @SneakyThrows
    private void index() {
        ControlPlane.waitForBoot();

        RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
                new HttpHost("localhost", 9200)));


        while (true) {
            try {

                Thread.sleep(1000);
                try (Connection c = ControlPlane.getApi().getConnection(Database.Datastore)) {

                    // Simple indexer saves up to ~1GB of RAM compared to logstash.
                    // https://github.com/hmcts/rse-cft-lib/blob/94aa0edeb0e1a4337a411ed8e6e20f170ed30bae/cftlib/lib/runtime/compose/logstash/logstash_conf.in#L3
                    var sql = """
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
                                  supplementary_data as json_supplementary_data,
                                  lower(case_type_id) || '_cases' as index_id,
                                  state,
                                  security_classification
                              from updated) row
                            """;

                    var p = c.prepareStatement(sql);
                    var results = p.executeQuery();
                    BulkRequest request = new BulkRequest();
                    while (results.next()) {
                        request.add(new IndexRequest(results.getString("index_id"))
                                .id(results.getString("id"))
                                .source(results.getString("row"), XContentType.JSON));
                    }
                    if (request.numberOfActions() > 0) {
                        var r = client.bulk(request, RequestOptions.DEFAULT);
                        if (r.hasFailures()) {
                            throw  new Exception();
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }
}

