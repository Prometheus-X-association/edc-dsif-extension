/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Handles automatic registration with a DSIF Sector Authority on startup.
 *
 * Checks /discovery to avoid duplicate registration, then POSTs
 * the full ConnectionProfile to /registerCatalog if not already registered.
 */
public class SectorAuthorityRegistrationService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DsifConfiguration config;
    private final DsifManifestService manifestService;
    private final Monitor monitor;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SectorAuthorityRegistrationService(DsifConfiguration config,
                                               DsifManifestService manifestService,
                                               Monitor monitor) {
        this.config = config;
        this.manifestService = manifestService;
        this.monitor = monitor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if this catalog is already registered with the Sector Authority.
     */
    public boolean isAlreadyRegistered() {
        String saUrl = config.getSectorAuthorityUrl();
        String discoveryUrl = saUrl + "/discovery";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                monitor.warning("SA discovery endpoint returned status " + response.statusCode());
                return false;
            }

            String body = response.body();
            String catalogId = config.getCatalogId();

            // Parse response and check if our catalogId appears in the catalogs array.
            // The discovery response contains a "catalogs" array with objects having a "catalogId" field.
            var tree = objectMapper.readTree(body);
            var catalogs = tree.get("catalogs");
            if (catalogs != null && catalogs.isArray()) {
                for (var catalog : catalogs) {
                    var idNode = catalog.get("catalogId");
                    if (idNode != null && catalogId.equals(idNode.asText())) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            monitor.warning("Failed to query SA discovery endpoint: " + e.getMessage());
            return false;
        }
    }

    /**
     * Register this catalog with the Sector Authority if not already registered.
     */
    public void register() {
        if (isAlreadyRegistered()) {
            monitor.info("Already registered with Sector Authority, skipping registration");
            return;
        }

        String saUrl = config.getSectorAuthorityUrl();
        String registerUrl = saUrl + "/registerCatalog";

        try {
            var connectionProfile = manifestService.buildConnectionProfile();
            String json = objectMapper.writeValueAsString(connectionProfile);

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(registerUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/ld+json");

            String apiKey = config.getSectorAuthorityApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                monitor.info("Registered with Sector Authority at " + saUrl);
            } else {
                monitor.warning("SA registration returned status " + response.statusCode()
                        + ": " + response.body());
            }
        } catch (Exception e) {
            monitor.warning("Failed to register with Sector Authority: " + e.getMessage());
        }
    }

    /**
     * Verify that this catalog appears in the SA discovery after registration.
     */
    public void verifyRegistration() {
        if (isAlreadyRegistered()) {
            monitor.info("Registration verified: catalog '" + config.getCatalogId()
                    + "' found in Sector Authority discovery");
        } else {
            monitor.warning("Registration verification failed: catalog '" + config.getCatalogId()
                    + "' not found in Sector Authority discovery");
        }
    }
}
