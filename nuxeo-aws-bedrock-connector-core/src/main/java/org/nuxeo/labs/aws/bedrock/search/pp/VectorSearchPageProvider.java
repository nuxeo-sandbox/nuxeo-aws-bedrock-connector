package org.nuxeo.labs.aws.bedrock.search.pp;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.fetcher.VcsFetcher;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.*;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;


public class VectorSearchPageProvider extends CoreQueryDocumentPageProvider {

    private static final Logger log = LogManager.getLogger(VectorSearchPageProvider.class);

    @Override
    public List<DocumentModel> getCurrentPage() {

        // use a cache
        if (currentPageDocuments != null) {
            return currentPageDocuments;
        }
        error = null;
        errorMessage = null;

        currentPageDocuments = new ArrayList<>();
        CoreSession coreSession = getCoreSession();
        if (query == null) {
            buildQuery(coreSession);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }

        NxQueryBuilder nxQuery = new NxQueryBuilder(coreSession).nxql(query);

        DocumentModel searchDoc = getSearchDocumentModel();

        if (searchDoc == null) {
            return getEmptyResult();
        }

        Map<String, String> namedParameters = (Map<String, String>) searchDoc.getContextData(NAMED_PARAMETERS);

        if (namedParameters == null) {
            return getEmptyResult();
        }

        String index = namedParameters.get("vector_index");
        String vector = namedParameters.get("vector_value");

        if (StringUtils.isBlank(vector)) {
            //get text input and create embedding
            String inputText = namedParameters.get("input_text");
            if (StringUtils.isBlank(inputText)) {
                return getEmptyResult();
            }

            //get embedding automation processor
            String chainName = namedParameters.get("embedding_automation_processor");

            AutomationService automationService = Framework.getService(AutomationService.class);
            OperationContext ctx = new OperationContext(coreSession);
            Map<String, Object> params = new HashMap<>();
            params.put("input_text", inputText);
            try {
                vector = (String) automationService.run(ctx, chainName, params);
            } catch (OperationException e) {
                throw new NuxeoException(e);
            }

            if (StringUtils.isBlank(vector)) {
                return getEmptyResult();
            }
        }

        float minScore = Float.parseFloat(namedParameters.getOrDefault("min_score", "0.4"));

        if (StringUtils.isBlank(index) || StringUtils.isBlank(vector)) {
            return getEmptyResult();
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
                """, namedParameters.get("vector_index"), vector, namedParameters.getOrDefault("k", "10")));

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(new SearchSourceBuilder().query(queryBuilder).postFilter(nxQuery.makeQuery()).minScore(minScore));

        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        ESClient client = esa.getClient();

        SearchResponse response = client.search(searchRequest);

        VcsFetcher fetcher = new VcsFetcher(getCoreSession(), response, null);

        SearchHits hits = response.getHits();

        List<DocumentModel> documents = fetcher.fetchDocuments();

        //reorder using relevance
        List<DocumentModel> result = new ArrayList<>();
        for (SearchHit hit : hits.getHits()) {
            Optional<DocumentModel> document = documents.stream().filter(doc -> doc.getId().equals(hit.getId())).findFirst();
            document.ifPresent(result::add);
        }

        currentPageDocuments = result;

        // set total number of hits
        setResultsCount(result.size());

        return result;
    }

    protected DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }

}
