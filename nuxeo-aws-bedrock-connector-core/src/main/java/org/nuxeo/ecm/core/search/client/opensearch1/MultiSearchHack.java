package org.nuxeo.ecm.core.search.client.opensearch1;

import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchServiceImpl;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;

/*
    This class is a hack to access protected attributes and methods form the search client
 */
public class MultiSearchHack {

    public static org.nuxeo.ecm.core.search.SearchResponse knnSearch(SearchServiceImpl searchService,
                                                                     SearchQuery searchQuery,
                                                                     QueryBuilder knnQueryBuilder, float minScore) {

        OpenSearchSearchClient client = (OpenSearchSearchClient) searchService.getClient("opensearch");
        OpenSearchQueryTransformer queryTransformer = client.queryTransformer;

        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(knnQueryBuilder)
                .from(searchQuery.getOffset()).minScore(minScore);

        //build nxql post filter
        QueryBuilder nxqlPostFilter = queryTransformer.makeQueryBuilder(searchQuery);

        //build aggregate post filter
        QueryBuilder aggregatePostFilter = queryTransformer.makeAggregatePostFilter(searchQuery);

        BoolQueryBuilder postFilter = QueryBuilders.boolQuery().must(nxqlPostFilter);

        if (aggregatePostFilter != null) {
            postFilter.must(aggregatePostFilter);
        }

        searchSourceBuilder.postFilter(postFilter);

        //add aggregates
        queryTransformer.makeAggregationBuilders(searchQuery).forEach(searchSourceBuilder::aggregation);

        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.client.search(searchRequest);

        return client.responseTransformer.apply(searchQuery, searchResponse);

        /* Need to reorder results with relevance score
        //reorder using relevance
        List<DocumentModel> result = new ArrayList<>();
        for (SearchHit hit : hits.getHits()) {
            Optional<DocumentModel> documentOpt = documents.stream().filter(doc -> doc.getId().equals(hit.getId())).findFirst();
            documentOpt.ifPresent(doc -> {
                doc.putContextData(RELEVANCE_SCORE,hit.getScore());
                result.add(doc);
            });
        }*/

    }


}
