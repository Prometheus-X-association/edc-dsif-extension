/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health check response as defined by DSIF PoC v0.2.
 */
public class HealthResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("version")
    private String version;

    @JsonProperty("uptime")
    private long uptime;

    @JsonProperty("dsifVersion")
    private String dsifVersion = "0.1.0";

    @JsonProperty("catalogId")
    private String catalogId;

    public HealthResponse() {}

    public HealthResponse(String status, String version, long uptime, String catalogId) {
        this.status = status;
        this.version = version;
        this.uptime = uptime;
        this.catalogId = catalogId;
    }

    public static HealthResponse ok(String version, long uptime, String catalogId) {
        return new HealthResponse("ok", version, uptime, catalogId);
    }

    public static HealthResponse degraded(String version, long uptime, String catalogId) {
        return new HealthResponse("degraded", version, uptime, catalogId);
    }

    public static HealthResponse error(String version, long uptime, String catalogId) {
        return new HealthResponse("error", version, uptime, catalogId);
    }

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public long getUptime() { return uptime; }
    public void setUptime(long uptime) { this.uptime = uptime; }
    public String getDsifVersion() { return dsifVersion; }
    public void setDsifVersion(String dsifVersion) { this.dsifVersion = dsifVersion; }
    public String getCatalogId() { return catalogId; }
    public void setCatalogId(String catalogId) { this.catalogId = catalogId; }
}
