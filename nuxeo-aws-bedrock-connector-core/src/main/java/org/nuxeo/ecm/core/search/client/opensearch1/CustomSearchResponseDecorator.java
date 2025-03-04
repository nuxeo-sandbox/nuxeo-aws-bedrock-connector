package org.nuxeo.ecm.core.search.client.opensearch1;

import org.jetbrains.annotations.Nullable;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.search.SearchClient;
import org.nuxeo.ecm.core.search.SearchHit;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchScrollContext;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.nuxeo.labs.aws.bedrock.search.pp.VectorSearchPageProvider.RELEVANCE_SCORE;

public class CustomSearchResponseDecorator implements SearchResponse {

    public SearchResponse delegate;
    public org.opensearch.action.search.SearchResponse osSearchResponse;

    public CustomSearchResponseDecorator(SearchResponse delegate, org.opensearch.action.search.SearchResponse osSearchResponse) {
        this.delegate = delegate;
        this.osSearchResponse = osSearchResponse;
    }

    @Override
    public long getTotal() {
        return delegate.getTotal();
    }

    @Override
    public boolean isTotalAccurate() {
        return delegate.isTotalAccurate();
    }

    @Override
    public long getHitsCount() {
        return delegate.getHitsCount();
    }

    @Override
    public List<SearchHit> getHits() {
        return delegate.getHits();
    }

    @Override
    public PartialList<Map<String, Serializable>> getHitsAsMap() {
        return delegate.getHitsAsMap();
    }

    @Override
    public IterableQueryResult getHitsAsIterator() {
        return delegate.getHitsAsIterator();
    }

    @Override
    public boolean isMissingCapabilities() {
        return delegate.isMissingCapabilities();
    }

    @Override
    public List<SearchClient.Capability> getMissingCapabilities() {
        return delegate.getMissingCapabilities();
    }

    @Nullable
    @Override
    public SearchScrollContext getScrollContext() {
        return delegate.getScrollContext();
    }

    @Override
    public List<Aggregate<? extends Bucket>> getAggregates() {
        return delegate.getAggregates();
    }

    @Override
    public DocumentModelList loadDocuments(CoreSession coreSession) {
        DocumentModelList docs =  delegate.loadDocuments(coreSession);

       /* Include relevance score*/
        for (org.opensearch.search.SearchHit hit : osSearchResponse.getHits()) {
            Optional<DocumentModel> documentOpt = docs.stream().filter(doc -> doc.getId().equals(hit.getId())).findFirst();
            documentOpt.ifPresent(doc -> {
                doc.putContextData(RELEVANCE_SCORE,hit.getScore());
            });
        }

        return docs;
    }
}
