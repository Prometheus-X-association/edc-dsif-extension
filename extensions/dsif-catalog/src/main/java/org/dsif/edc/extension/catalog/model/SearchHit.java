/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Individual search hit as defined by DSIF PoC v0.2 Neutral Search.
 */
public class SearchHit {

    @JsonProperty("uri")
    private String uri;

    @JsonProperty("type")
    private String type;

    @JsonProperty("title")
    private String title;

    @JsonProperty("publisher")
    private String publisher;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("keywords")
    private List<String> keywords;

    @JsonProperty("formats")
    private List<String> formats;

    @JsonProperty("provenance")
    private Provenance provenance;

    public SearchHit() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SearchHit hit = new SearchHit();

        public Builder uri(String uri) {
            hit.uri = uri;
            return this;
        }

        public Builder type(String type) {
            hit.type = type;
            return this;
        }

        public Builder title(String title) {
            hit.title = title;
            return this;
        }

        public Builder publisher(String publisher) {
            hit.publisher = publisher;
            return this;
        }

        public Builder summary(String summary) {
            hit.summary = summary;
            return this;
        }

        public Builder keywords(List<String> keywords) {
            hit.keywords = keywords;
            return this;
        }

        public Builder formats(List<String> formats) {
            hit.formats = formats;
            return this;
        }

        public Builder provenance(Provenance provenance) {
            hit.provenance = provenance;
            return this;
        }

        public SearchHit build() {
            return hit;
        }
    }

    // Getters and Setters
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public List<String> getFormats() { return formats; }
    public void setFormats(List<String> formats) { this.formats = formats; }
    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }
}
