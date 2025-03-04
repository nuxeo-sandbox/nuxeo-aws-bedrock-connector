package org.nuxeo.ecm.core.search.client.opensearch1;

import org.nuxeo.ecm.core.search.*;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;

/*
    This class is a hack to access protected attributes and methods form the search client
 */
public class MultiSearchHack {

    public static SearchResponse knnSearch(SearchServiceImpl searchService,
                                           SearchQuery searchQuery,
                                           QueryBuilder knnQueryBuilder, float minScore) {

        OpenSearchSearchClient client = (OpenSearchSearchClient) searchService.getClient("opensearch");
        OpenSearchQueryTransformer queryTransformer = client.queryTransformer;

        SearchRequest osSearchRequest = customQueryTransformerApply(queryTransformer, searchQuery, knnQueryBuilder, minScore);

        org.opensearch.action.search.SearchResponse osSearchResponse = client.client.search(osSearchRequest);

        return new CustomSearchResponseDecorator(client.responseTransformer.apply(searchQuery, osSearchResponse),osSearchResponse);

    }

    public static SearchRequest customQueryTransformerApply(OpenSearchQueryTransformer queryTransformer,
                                                            SearchQuery searchQuery,
                                                            QueryBuilder knnQueryBuilder, float minScore) {
        SearchRequest searchRequest = new SearchRequest();

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(knnQueryBuilder)
                .from(searchQuery.getOffset()).minScore(minScore);

        // build nxql filter
        QueryBuilder nxqlFilter = queryTransformer.makeQueryBuilder(searchQuery);

        // build aggregate filter
        QueryBuilder aggregatePostFilter = queryTransformer.makeAggregatePostFilter(searchQuery);

        // build full post filter
        BoolQueryBuilder postFilter = QueryBuilders.boolQuery().must(nxqlFilter);
        if (aggregatePostFilter != null) {
            postFilter.must(aggregatePostFilter);
        }
        searchSourceBuilder.postFilter(postFilter);

        //add aggregates
        queryTransformer.makeAggregationBuilders(searchQuery).forEach(searchSourceBuilder::aggregation);

        searchRequest.source(searchSourceBuilder);

        return searchRequest;
    }
}
