/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Federation Manifest (IC Manifest) as defined by DSIF PoC v0.2.
 *
 * Served at /.well-known/dataspace-catalog.json
 * Provides self-description for Independent Catalogs.
 */
public class FederationManifest {

    @JsonProperty("@context")
    private List<Object> context;

    @JsonProperty("@id")
    private String id;

    @JsonProperty("@type")
    private String type = "dsif:ICManifest";

    @JsonProperty("dct:title")
    private String title;

    @JsonProperty("dct:description")
    private String description;

    @JsonProperty("dct:publisher")
    private Publisher publisher;

    @JsonProperty("dct:conformsTo")
    private List<Map<String, String>> conformsTo;

    @JsonProperty("dsif:sector")
    private String sector;

    @JsonProperty("dsif:hasConnectionProfile")
    private ConnectionProfileRef connectionProfile;

    @JsonProperty("dsif:ttlSeconds")
    private int ttlSeconds;

    @JsonProperty("dsif:version")
    private String dsifVersion = "0.1.0";

    @JsonProperty("schema:contactPoint")
    private ContactPoint contactPoint;

    // Builder pattern for construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final FederationManifest manifest = new FederationManifest();

        public Builder context(List<Object> context) {
            manifest.context = context;
            return this;
        }

        public Builder id(String id) {
            manifest.id = id;
            return this;
        }

        public Builder title(String title) {
            manifest.title = title;
            return this;
        }

        public Builder description(String description) {
            manifest.description = description;
            return this;
        }

        public Builder publisher(Publisher publisher) {
            manifest.publisher = publisher;
            return this;
        }

        public Builder conformsTo(List<Map<String, String>> conformsTo) {
            manifest.conformsTo = conformsTo;
            return this;
        }

        public Builder sector(String sector) {
            manifest.sector = sector;
            return this;
        }

        public Builder connectionProfile(ConnectionProfileRef connectionProfile) {
            manifest.connectionProfile = connectionProfile;
            return this;
        }

        public Builder ttlSeconds(int ttlSeconds) {
            manifest.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder dsifVersion(String dsifVersion) {
            manifest.dsifVersion = dsifVersion;
            return this;
        }

        public Builder contactPoint(ContactPoint contactPoint) {
            manifest.contactPoint = contactPoint;
            return this;
        }

        public FederationManifest build() {
            return manifest;
        }
    }

    // Getters
    public List<Object> getContext() { return context; }
    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Publisher getPublisher() { return publisher; }
    public List<Map<String, String>> getConformsTo() { return conformsTo; }
    public String getSector() { return sector; }
    public ConnectionProfileRef getConnectionProfile() { return connectionProfile; }
    public int getTtlSeconds() { return ttlSeconds; }
    public String getDsifVersion() { return dsifVersion; }
    public ContactPoint getContactPoint() { return contactPoint; }
}
