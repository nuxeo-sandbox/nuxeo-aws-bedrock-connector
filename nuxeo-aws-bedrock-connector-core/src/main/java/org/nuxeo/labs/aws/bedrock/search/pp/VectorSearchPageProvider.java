package org.nuxeo.labs.aws.bedrock.search.pp;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.json.JSONArray;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.api.EsResult;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;


public class VectorSearchPageProvider extends ElasticSearchNxqlPageProvider {

    public static final String RELEVANCE_SCORE = "relevance_score";

    private static final Logger log = LogManager.getLogger(VectorSearchPageProvider.class);

    @Override
    public List<DocumentModel> getCurrentPage() {

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
        CoreSession coreSession = getCoreSession();
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

        NxQueryBuilder nxQuery = this.getQueryBuilder(coreSession);

        // Combine NXQL and KNN using "must" (AND logic)
        BoolQueryBuilder combinedQuery = QueryBuilders
                .boolQuery();

        combinedQuery = combinedQuery.must(buildKnnScriptQuery(vector, namedParameters.get("vector_index"))).boost(1.0f);

        if (searchOnAllRepositories()) {
            nxQuery.searchOnAllRepositories();
        }
        nxQuery.useUnrestrictedSession(useUnrestrictedSession());

        List<String> highlightFields = getHighlights();
        if (highlightFields != null && !highlightFields.isEmpty()) {
            nxQuery.highlight(highlightFields);
        }

        combinedQuery = combinedQuery.filter(getCurrentQueryAsEsBuilder());
        nxQuery = nxQuery.esQuery(combinedQuery)
                .fetchFromElasticsearch() // Force ES query
                .offset((int) this.getCurrentPageOffset())
                .limit(this.getLimit())
                .addAggregates(this.buildAggregates());
        log.debug("ES KNN query: " + nxQuery.makeQuery());

        ElasticSearchService esService = Framework.getService(ElasticSearchService.class);
        EsResult ret = esService.queryAndAggregate(nxQuery);
        DocumentModelList dmList = ret.getDocuments();

        currentAggregates = new HashMap<>(ret.getAggregates().size());
        for (Aggregate<Bucket> agg : ret.getAggregates()) {
            currentAggregates.put(agg.getId(), agg);
        }
        setResultsCount(dmList.totalSize());
        currentPageDocuments = dmList;
        return currentPageDocuments;
    }

    public DocumentModelList getEmptyResult() {
        setResultsCount(0);
        return new DocumentModelListImpl();
    }

    private ScriptScoreQueryBuilder buildKnnScriptQuery(String queryVector, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("query_vector", parseVector(queryVector));

        // Construct the script for cosine similarity
        Script script = new Script(ScriptType.INLINE, "painless",
                "cosineSimilarity(params.query_vector, '" + type + "') + 1.0", params);

        // Build the script-based query
        return QueryBuilders.scriptScoreQuery(
                QueryBuilders.existsQuery(type), // Filter: Only docs with vectors
                script
        );
    }

    private double[] parseVector(String vectorString) {
        JSONArray jsonArray = new JSONArray(vectorString);

        double[] result = new double[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            result[i] = jsonArray.getDouble(i);
        }

        return result;
    }


}
