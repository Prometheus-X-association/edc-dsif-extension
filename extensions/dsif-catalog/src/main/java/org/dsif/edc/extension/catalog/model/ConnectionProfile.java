/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Connection Profile as defined by DSIF PoC v0.2.
 *
 * Contains endpoint information and capabilities for catalog discovery.
 * Used by Sector Authorities for IC registration and discovery.
 */
public class ConnectionProfile {

    @JsonProperty("@context")
    private List<Object> context;

    @JsonProperty("@id")
    private String id;

    @JsonProperty("@type")
    private String type = "dsif:ConnectionProfile";

    @JsonProperty("dct:title")
    private String title;

    @JsonProperty("dct:conformsTo")
    private List<Map<String, String>> conformsTo;

    @JsonProperty("dsif:endpoints")
    private EndpointSet endpoints;

    @JsonProperty("dsif:searchTemplate")
    private String searchTemplate;

    @JsonProperty("dsif:searchParameters")
    private List<String> searchParameters;

    @JsonProperty("dsif:auth")
    private AuthConfig auth;

    @JsonProperty("dsif:ttlSeconds")
    private int ttlSeconds;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ConnectionProfile profile = new ConnectionProfile();

        public Builder context(List<Object> context) {
            profile.context = context;
            return this;
        }

        public Builder id(String id) {
            profile.id = id;
            return this;
        }

        public Builder title(String title) {
            profile.title = title;
            return this;
        }

        public Builder conformsTo(List<Map<String, String>> conformsTo) {
            profile.conformsTo = conformsTo;
            return this;
        }

        public Builder endpoints(EndpointSet endpoints) {
            profile.endpoints = endpoints;
            return this;
        }

        public Builder searchTemplate(String searchTemplate) {
            profile.searchTemplate = searchTemplate;
            return this;
        }

        public Builder searchParameters(List<String> searchParameters) {
            profile.searchParameters = searchParameters;
            return this;
        }

        public Builder auth(AuthConfig auth) {
            profile.auth = auth;
            return this;
        }

        public Builder ttlSeconds(int ttlSeconds) {
            profile.ttlSeconds = ttlSeconds;
            return this;
        }

        public ConnectionProfile build() {
            return profile;
        }
    }

    // Getters
    public List<Object> getContext() { return context; }
    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public List<Map<String, String>> getConformsTo() { return conformsTo; }
    public EndpointSet getEndpoints() { return endpoints; }
    public String getSearchTemplate() { return searchTemplate; }
    public List<String> getSearchParameters() { return searchParameters; }
    public AuthConfig getAuth() { return auth; }
    public int getTtlSeconds() { return ttlSeconds; }
}
