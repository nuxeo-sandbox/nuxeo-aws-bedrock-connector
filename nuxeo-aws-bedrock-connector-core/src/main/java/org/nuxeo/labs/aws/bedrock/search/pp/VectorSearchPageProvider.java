package org.nuxeo.labs.aws.bedrock.search.pp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.io.DocumentModelReaders;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.UNSUPPORTED_ACL;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.ACL_FIELD;

public class VectorSearchPageProvider extends CoreQueryDocumentPageProvider {

    private static final Logger log = LogManager.getLogger(VectorSearchPageProvider.class);

    @Override
    public List<DocumentModel> getCurrentPage() {

        List<Double> vector = (List<Double>) getSearchDocumentModel().getPropertyValue("simple-vector-search_pp:vector");

        QueryBuilder queryBuilder = QueryBuilders.wrapperQuery(String.format("""
                {
                    "knn": {
                        "embedding:image": {
                            "vector": %s
                         },
                        "filter": %s
                    }
                }
                """, vector, getSecurityFilter().toString()));

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(new SearchSourceBuilder().query(queryBuilder));

        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        ESClient client = esa.getClient();

        SearchResponse response = client.search(searchRequest);

        SearchHits hits = response.getHits();

        List<DocumentModel> entries = new ArrayList<>();

        for (SearchHit hit : hits) {
            if (hit.getScore() > 0.5) {
                DocumentModel doc = DocumentModelReaders.fromSource(hit.getSourceAsMap()).session(getCoreSession()).getDocumentModel();
                entries.add(doc);
            }
        }

        // set total number of hits
        setResultsCount(entries.size());

        return entries;
    }


    protected QueryBuilder getSecurityFilter() {
        NuxeoPrincipal principal = getCoreSession().getPrincipal();
        if (principal == null || principal.isAdministrator()) {
            return QueryBuilders.matchAllQuery();
        }
        String[] principals = SecurityService.getPrincipalsToCheck(principal);
        // we want an ACL that match principals but we discard unsupported ACE that contains negative ACE
        return  QueryBuilders.boolQuery()
                .must(QueryBuilders.termsQuery(ACL_FIELD, principals))
                .mustNot(QueryBuilders.termsQuery(ACL_FIELD, UNSUPPORTED_ACL));
    }


}
