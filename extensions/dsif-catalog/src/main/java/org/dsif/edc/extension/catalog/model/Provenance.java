/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Provenance information for federated search results.
 */
public class Provenance {

    @JsonProperty("originCatalogId")
    private String originCatalogId;

    @JsonProperty("federationPath")
    private List<String> federationPath;

    @JsonProperty("hopCount")
    private int hopCount;

    public Provenance() {}

    public Provenance(String originCatalogId, List<String> federationPath, int hopCount) {
        this.originCatalogId = originCatalogId;
        this.federationPath = federationPath;
        this.hopCount = hopCount;
    }

    /**
     * Create provenance for local catalog (IC serving its own data).
     */
    public static Provenance local(String catalogId) {
        return new Provenance(catalogId, List.of(), 0);
    }

    // Getters and Setters
    public String getOriginCatalogId() { return originCatalogId; }
    public void setOriginCatalogId(String originCatalogId) { this.originCatalogId = originCatalogId; }
    public List<String> getFederationPath() { return federationPath; }
    public void setFederationPath(List<String> federationPath) { this.federationPath = federationPath; }
    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }
}
