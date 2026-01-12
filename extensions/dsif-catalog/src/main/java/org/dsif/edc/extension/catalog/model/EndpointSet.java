/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Set of DSIF endpoints for a catalog.
 */
public class EndpointSet {

    @JsonProperty("@type")
    private String type = "dsif:EndpointSet";

    @JsonProperty("dsif:catalogEndpoint")
    private Map<String, String> catalogEndpoint;

    @JsonProperty("dsif:datasetEndpoint")
    private Map<String, String> datasetEndpoint;

    @JsonProperty("dsif:searchEndpoint")
    private Map<String, String> searchEndpoint;

    @JsonProperty("dsif:healthEndpoint")
    private Map<String, String> healthEndpoint;

    public EndpointSet() {}

    public EndpointSet(String catalogUrl, String datasetUrl, String searchUrl, String healthUrl) {
        this.catalogEndpoint = Map.of("@id", catalogUrl);
        this.datasetEndpoint = Map.of("@id", datasetUrl);
        this.searchEndpoint = Map.of("@id", searchUrl);
        this.healthEndpoint = Map.of("@id", healthUrl);
    }

    public String getType() { return type; }
    public Map<String, String> getCatalogEndpoint() { return catalogEndpoint; }
    public void setCatalogEndpoint(Map<String, String> catalogEndpoint) { this.catalogEndpoint = catalogEndpoint; }
    public Map<String, String> getDatasetEndpoint() { return datasetEndpoint; }
    public void setDatasetEndpoint(Map<String, String> datasetEndpoint) { this.datasetEndpoint = datasetEndpoint; }
    public Map<String, String> getSearchEndpoint() { return searchEndpoint; }
    public void setSearchEndpoint(Map<String, String> searchEndpoint) { this.searchEndpoint = searchEndpoint; }
    public Map<String, String> getHealthEndpoint() { return healthEndpoint; }
    public void setHealthEndpoint(Map<String, String> healthEndpoint) { this.healthEndpoint = healthEndpoint; }
}
