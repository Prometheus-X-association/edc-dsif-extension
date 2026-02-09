/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.Provenance;
import org.dsif.edc.extension.catalog.model.SearchHit;
import org.dsif.edc.extension.catalog.model.SearchResult;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for DSIF Neutral Search functionality.
 *
 * Uses EDC's AssetIndex and QuerySpec for filtering, pagination, and sorting.
 * Transforms results to DSIF SearchResult format.
 */
public class DsifSearchService {

    private final DsifConfiguration config;
    private final AssetIndex assetIndex;

    public DsifSearchService(DsifConfiguration config, AssetIndex assetIndex) {
        this.config = config;
        this.assetIndex = assetIndex;
    }

    /**
     * Perform a neutral search on the catalog using EDC's QuerySpec.
     *
     * @param params Search parameters
     * @return SearchResult with matching hits in DSIF format
     */
    public SearchResult search(SearchParams params) {
        // Validate and normalize parameters
        int page = Math.max(1, params.getPage());
        int pageSize = normalizePageSize(params.getPageSize());
        int offset = (page - 1) * pageSize;

        // Build QuerySpec using EDC's filtering infrastructure
        QuerySpec.Builder queryBuilder = QuerySpec.Builder.newInstance()
                .offset(offset)
                .limit(pageSize);

        // Add filter criteria using EDC's Criterion
        List<Criterion> criteria = buildCriteria(params);
        for (Criterion criterion : criteria) {
            queryBuilder.filter(criterion);
        }

        // Apply sorting
        applySorting(queryBuilder, params.getSort());

        // Execute query using EDC's AssetIndex
        QuerySpec querySpec = queryBuilder.build();
        List<Asset> assets = assetIndex.queryAssets(querySpec)
                .filter(asset -> matchesFullText(asset, params.getQ()))
                .filter(asset -> matchesType(asset, params.getType()))
                .collect(Collectors.toList());

        // Transform EDC Assets to DSIF SearchHits
        List<SearchHit> hits = assets.stream()
                .map(this::transformToSearchHit)
                .collect(Collectors.toList());

        // Count total matching assets
        long total = countMatchingAssets(params);

        return SearchResult.builder()
                .total((int) total)
                .page(page)
                .pageSize(pageSize)
                .hits(hits)
                .build();
    }

    /**
     * Transform an EDC Asset to a DSIF SearchHit.
     */
    private SearchHit transformToSearchHit(Asset asset) {
        Map<String, Object> props = asset.getProperties();

        String assetType = getStringProp(props, "type", "dataset");
        boolean isService = "service".equalsIgnoreCase(assetType) ||
                "dataservice".equalsIgnoreCase(assetType);

        return SearchHit.builder()
                .uri(buildAssetUri(asset.getId()))
                .assetId(asset.getId())
                .type(isService ? "service" : "dataset")
                .title(getStringProp(props, "name", asset.getId()))
                .publisher(getStringProp(props, "publisher", null))
                .summary(truncateSummary(getStringProp(props, "description", null)))
                .keywords(parseKeywords(props))
                .formats(parseFormats(props))
                .provenance(Provenance.local(config.getCatalogId()))
                .pricingTier(getStringProp(props, "pricingTier", null))
                .build();
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize <= 0) {
            return config.getSearchDefaultPageSize();
        }
        return Math.min(requestedSize, config.getSearchMaxPageSize());
    }

    /**
     * Build EDC Criterion list from search parameters.
     */
    private List<Criterion> buildCriteria(SearchParams params) {
        List<Criterion> criteria = new ArrayList<>();

        // Format filter
        if (params.getFormat() != null && !params.getFormat().isEmpty()) {
            criteria.add(new Criterion("contenttype", "=", params.getFormat()));
        }

        // Publisher filter
        if (params.getPublisher() != null && !params.getPublisher().isEmpty()) {
            criteria.add(new Criterion("publisher", "=", params.getPublisher()));
        }

        // Keyword filter (partial match using 'like')
        if (params.getKeyword() != null && !params.getKeyword().isEmpty()) {
            criteria.add(new Criterion("keywords", "like", "%" + params.getKeyword() + "%"));
        }

        return criteria;
    }

    /**
     * Apply sorting to QuerySpec builder.
     */
    private void applySorting(QuerySpec.Builder queryBuilder, String sort) {
        if (sort == null || sort.isEmpty() || "relevance".equals(sort)) {
            // Default: no specific sorting
            return;
        }

        switch (sort) {
            case "recent":
                queryBuilder.sortField("modified");
                queryBuilder.sortOrder(SortOrder.DESC);
                break;
            case "title":
                queryBuilder.sortField("name");
                queryBuilder.sortOrder(SortOrder.ASC);
                break;
        }
    }

    /**
     * Full-text search across asset properties.
     * Note: EDC's QuerySpec doesn't support full-text search natively,
     * so we apply this as a post-filter.
     */
    private boolean matchesFullText(Asset asset, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }

        String lowerQuery = query.toLowerCase();
        Map<String, Object> props = asset.getProperties();

        // Search in name
        String name = getStringProp(props, "name", "");
        if (name.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Search in description
        String description = getStringProp(props, "description", "");
        if (description.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Search in keywords
        Object keywords = props.get("keywords");
        if (keywords != null && keywords.toString().toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Search in asset ID
        if (asset.getId().toLowerCase().contains(lowerQuery)) {
            return true;
        }

        return false;
    }

    /**
     * Filter by asset type (dataset/service).
     */
    private boolean matchesType(Asset asset, String type) {
        if (type == null || type.isEmpty()) {
            return true;
        }

        String assetType = getStringProp(asset.getProperties(), "type", "dataset");

        if ("dataset".equalsIgnoreCase(type)) {
            return !"service".equalsIgnoreCase(assetType) &&
                    !"dataservice".equalsIgnoreCase(assetType);
        }

        if ("service".equalsIgnoreCase(type)) {
            return "service".equalsIgnoreCase(assetType) ||
                    "dataservice".equalsIgnoreCase(assetType);
        }

        return true;
    }

    private long countMatchingAssets(SearchParams params) {
        // Use max query spec to count all matching assets
        QuerySpec countSpec = QuerySpec.max();

        return assetIndex.queryAssets(countSpec)
                .filter(asset -> matchesFullText(asset, params.getQ()))
                .filter(asset -> matchesType(asset, params.getType()))
                .count();
    }

    private String buildAssetUri(String assetId) {
        return String.format("urn:dsif:%s:%s", config.getCatalogId(), assetId);
    }

    private String truncateSummary(String description) {
        if (description == null) {
            return null;
        }
        if (description.length() <= 200) {
            return description;
        }
        return description.substring(0, 197) + "...";
    }

    private List<String> parseKeywords(Map<String, Object> props) {
        Object keywords = props.get("keywords");
        if (keywords == null) {
            keywords = props.get("dcat:keyword");
        }
        if (keywords == null) {
            return Collections.emptyList();
        }

        if (keywords instanceof List) {
            return ((List<?>) keywords).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        if (keywords instanceof String) {
            String keywordStr = (String) keywords;
            if (keywordStr.contains(",")) {
                return List.of(keywordStr.split(",")).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return List.of(keywordStr.trim());
        }

        return Collections.emptyList();
    }

    private List<String> parseFormats(Map<String, Object> props) {
        String contentType = getStringProp(props, "contenttype", null);
        if (contentType != null) {
            return List.of(contentType);
        }
        return Collections.emptyList();
    }

    private String getStringProp(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        if (value == null) {
            value = props.get("asset:prop:" + key);
        }
        if (value == null) {
            value = props.get("edc:" + key);
        }
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Search parameters holder.
     */
    public static class SearchParams {
        private String q;
        private String type;
        private String format;
        private String publisher;
        private String keyword;
        private int page = 1;
        private int pageSize = 10;
        private String sort;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final SearchParams params = new SearchParams();

            public Builder q(String q) { params.q = q; return this; }
            public Builder type(String type) { params.type = type; return this; }
            public Builder format(String format) { params.format = format; return this; }
            public Builder publisher(String publisher) { params.publisher = publisher; return this; }
            public Builder keyword(String keyword) { params.keyword = keyword; return this; }
            public Builder page(int page) { params.page = page; return this; }
            public Builder pageSize(int pageSize) { params.pageSize = pageSize; return this; }
            public Builder sort(String sort) { params.sort = sort; return this; }
            public SearchParams build() { return params; }
        }

        public String getQ() { return q; }
        public String getType() { return type; }
        public String getFormat() { return format; }
        public String getPublisher() { return publisher; }
        public String getKeyword() { return keyword; }
        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }
        public String getSort() { return sort; }
    }
}
