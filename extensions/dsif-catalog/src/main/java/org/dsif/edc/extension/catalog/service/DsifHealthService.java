/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.HealthResponse;

import java.time.Instant;

/**
 * Service for health monitoring and status reporting.
 */
public class DsifHealthService {

    private final DsifConfiguration config;
    private final Instant startTime;
    private final String version;

    public DsifHealthService(DsifConfiguration config, String extensionVersion) {
        this.config = config;
        this.startTime = Instant.now();
        this.version = extensionVersion;
    }

    /**
     * Get current health status.
     */
    public HealthResponse getHealth() {
        long uptimeSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        return HealthResponse.ok(version, uptimeSeconds, config.getCatalogId());
    }

    /**
     * Get uptime in seconds since extension started.
     */
    public long getUptimeSeconds() {
        return Instant.now().getEpochSecond() - startTime.getEpochSecond();
    }

    /**
     * Get the extension version.
     */
    public String getVersion() {
        return version;
    }
}
