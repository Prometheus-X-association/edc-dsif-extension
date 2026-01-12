/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Search result response as defined by DSIF PoC v0.2 Neutral Search.
 */
public class SearchResult {

    @JsonProperty("total")
    private int total;

    @JsonProperty("page")
    private int page;

    @JsonProperty("pageSize")
    private int pageSize;

    @JsonProperty("hits")
    private List<SearchHit> hits;

    public SearchResult() {}

    public SearchResult(int total, int page, int pageSize, List<SearchHit> hits) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.hits = hits;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int total;
        private int page;
        private int pageSize;
        private List<SearchHit> hits;

        public Builder total(int total) {
            this.total = total;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder hits(List<SearchHit> hits) {
            this.hits = hits;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(total, page, pageSize, hits);
        }
    }

    // Getters and Setters
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public List<SearchHit> getHits() { return hits; }
    public void setHits(List<SearchHit> hits) { this.hits = hits; }
}
