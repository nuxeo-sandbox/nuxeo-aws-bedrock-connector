package org.nuxeo.labs.aws.bedrock.search.pp;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.search.*;
import org.nuxeo.ecm.core.search.client.opensearch1.MultiSearchHack;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.nxql.SearchServicePageProvider;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

import java.util.*;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;


public class VectorSearchPageProvider extends SearchServicePageProvider {

    public static final String RELEVANCE_SCORE = "relevance_score";

    private static final Logger log = LogManager.getLogger(VectorSearchPageProvider.class);

    @Override
    public List<DocumentModel> getCurrentPage() {
        long t0 = System.currentTimeMillis();

        // use a cache
        if (currentPageDocuments != null) {
            return currentPageDocuments;
        }

        //fallback to default implementation if there is no vector search
        DocumentModel searchDoc = getSearchDocumentModel();
        if (searchDoc == null) {
            return getEmptyResult();
        }

        Map<String, String> namedParameters = (Map<String, String>) searchDoc.getContextData(NAMED_PARAMETERS);
        if (namedParameters == null) {
            return super.getCurrentPage();
        }

        CoreSession coreSession = getCoreSession();
        NuxeoPrincipal principal = coreSession.getPrincipal();
        if (useUnrestrictedSession() && !principal.isAdministrator()) {
            coreSession = CoreInstance.getCoreSessionSystem(coreSession.getRepositoryName(), principal.getName());
            principal = coreSession.getPrincipal();
        }


        String index = namedParameters.get("vector_index");
        String vector = namedParameters.get("vector_value");
        String inputText = namedParameters.get("input_text");
        if (index == null && vector == null && inputText == null) {
            return super.getCurrentPage();
        }

        // proceed with vector search implementation
        error = null;
        errorMessage = null;
        currentPageDocuments = new ArrayList<>();

        if (query == null) {
            buildQuery(coreSession);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }

        if (StringUtils.isBlank(vector)) {
            //get text input and create embedding
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

        QueryBuilder knnQueryBuilder = QueryBuilders.wrapperQuery(String.format("""
                {
                    "knn": {
                        "%s": {
                            "vector": %s,
                            "k": %s
                         }
                    }
                }
                """, namedParameters.get("vector_index"), vector, namedParameters.getOrDefault("k", "10")));

        try {
            SearchServiceImpl searchService = (SearchServiceImpl) Framework.getService(SearchService.class);

            List<SearchIndex> searchIndexes = this.getSearchIndexes(searchService, coreSession.getRepositoryName());
            SearchQueryImpl.Builder searchQueryBuilder = SearchQuery.builder(searchIndexes, this.query, principal)
                    .offset((int) this.getCurrentPageOffset()).limit(this.getLimit())
                    .addAggregates(this.buildAggregates()).addHighlights(ListUtils.emptyIfNull(this.getHighlights()));


            SearchResponse searchResponse = MultiSearchHack.knnSearch(searchService, searchQueryBuilder.build(), knnQueryBuilder, minScore);

            DocumentModelList dmList = searchResponse.loadDocuments(coreSession);
            this.currentAggregates = new HashMap<>(searchResponse.getAggregates().size());

            for (Aggregate<? extends Bucket> agg : searchResponse.getAggregates()) {
                this.currentAggregates.put(agg.getId(), agg);
            }

            this.setResultsCount(searchResponse.getTotal());
            this.currentPageDocuments = dmList;

        } catch (QueryParseException e) {
            this.error = e;
            this.errorMessage = e.getMessage();
            log.warn(e.getMessage(), e);
        }

        this.fireSearchEvent(this.getCoreSession().getPrincipal(), this.query, this.currentPageDocuments, System.currentTimeMillis() - t0);
        return this.currentPageDocuments;
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }

}
