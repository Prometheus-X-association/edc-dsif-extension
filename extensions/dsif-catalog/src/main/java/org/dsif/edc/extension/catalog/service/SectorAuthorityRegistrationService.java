/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Handles automatic registration with a DSIF Sector Authority on startup.
 *
 * Checks /discovery to avoid duplicate registration, then POSTs
 * the full ConnectionProfile to /registerCatalog if not already registered.
 */
public class SectorAuthorityRegistrationService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DsifConfiguration config;
    private final Monitor monitor;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SectorAuthorityRegistrationService(DsifConfiguration config,
                                               Monitor monitor) {
        this.config = config;
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
            String json = objectMapper.writeValueAsString(buildRegistrationPayload());

            monitor.info("SA registration POST to: " + registerUrl);
            monitor.info("SA registration payload: " + json);

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(registerUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json");

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
     * Build the plain JSON registration payload expected by the SA.
     */
    private ObjectNode buildRegistrationPayload() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("catalogId", config.getCatalogId());
        root.put("type", "ic");
        root.put("sector", config.getCatalogSector());
        root.put("description", config.getCatalogDescription());

        // Endpoints
        ObjectNode endpoints = objectMapper.createObjectNode();
        endpoints.put("catalog", config.getCatalogUrl());
        endpoints.put("dataset", config.getDatasetUrlTemplate());
        endpoints.put("search", config.getSearchUrl());
        endpoints.put("health", config.getHealthUrl());
        root.set("endpoints", endpoints);

        // Conformance
        ArrayNode conformsTo = objectMapper.createArrayNode();
        conformsTo.add("https://www.w3.org/TR/vocab-dcat-3/");
        conformsTo.add("https://extensions.ckan.org/extension/dcat/");
        root.set("conformsTo", conformsTo);

        // Capabilities
        ObjectNode capabilities = objectMapper.createObjectNode();

        ObjectNode searchCap = objectMapper.createObjectNode();
        searchCap.put("q", true);
        ArrayNode filters = objectMapper.createArrayNode();
        for (String f : List.of("type", "format", "publisher", "keyword")) {
            filters.add(f);
        }
        searchCap.set("filters", filters);
        searchCap.put("paging", true);
        ArrayNode sort = objectMapper.createArrayNode();
        for (String s : List.of("relevance", "recent", "title")) {
            sort.add(s);
        }
        searchCap.set("sort", sort);
        capabilities.set("search", searchCap);

        ObjectNode catalogCap = objectMapper.createObjectNode();
        catalogCap.put("pagination", true);
        catalogCap.put("format", "application/ld+json");
        capabilities.set("catalog", catalogCap);

        ObjectNode authCap = objectMapper.createObjectNode();
        authCap.put("scheme", config.getAuthScheme());
        capabilities.set("auth", authCap);

        root.set("capabilities", capabilities);

        // Search template and parameters
        root.put("searchTemplate", config.getSearchTemplate());
        ArrayNode searchParameters = objectMapper.createArrayNode();
        for (String p : List.of("q", "keyword", "type", "format", "page", "pageSize", "sort")) {
            searchParameters.add(p);
        }
        root.set("searchParameters", searchParameters);

        // Auth
        ObjectNode auth = objectMapper.createObjectNode();
        auth.put("scheme", config.getAuthScheme());
        root.set("auth", auth);

        // Timestamps and TTL
        root.put("lastUpdated", Instant.now().toString());
        root.put("ttlSeconds", config.getTtlSeconds());

        return root;
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
