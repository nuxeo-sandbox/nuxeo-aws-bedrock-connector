package org.nuxeo.labs.aws.bedrock.search.pp;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.fetcher.VcsFetcher;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.UNSUPPORTED_ACL;
import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.ACL_FIELD;

public class VectorSearchPageProvider extends CoreQueryDocumentPageProvider {

    private static final Logger log = LogManager.getLogger(VectorSearchPageProvider.class);

    @Override
    public List<DocumentModel> getCurrentPage() {

        Map<String,String> namedParameters = (Map<String, String>) getSearchDocumentModel().getContextData(NAMED_PARAMETERS);

        String index = namedParameters.get("vector_index");
        String vector = namedParameters.get("vector_value");

        if (StringUtils.isBlank(index) || StringUtils.isBlank(vector)) {
            setResultsCount(0);
            return new DocumentModelListImpl();
        }

        QueryBuilder queryBuilder = QueryBuilders.wrapperQuery(String.format("""
                {
                    "knn": {
                        "%s": {
                            "vector": %s,
                            "k": %s
                         }
                    }
                }
                """, namedParameters.get("vector_index"), namedParameters.get("vector_value"),namedParameters.getOrDefault("k","10")));

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(new SearchSourceBuilder().query(queryBuilder).postFilter(getSecurityFilter()).minScore(0.5f));

        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        ESClient client = esa.getClient();

        SearchResponse response = client.search(searchRequest);

        VcsFetcher fetcher = new VcsFetcher(getCoreSession(),response, null);

        SearchHits hits = response.getHits();

        List<DocumentModel> documents = fetcher.fetchDocuments();

        //reorder using relevance
        List<DocumentModel> result = new ArrayList<>();
        for (SearchHit hit: hits.getHits()) {
            Optional<DocumentModel> document = documents.stream().filter(doc -> doc.getId().equals(hit.getId())).findFirst();
            document.ifPresent(result::add);
        }

        // set total number of hits
        setResultsCount(result.size());

        return result;
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
