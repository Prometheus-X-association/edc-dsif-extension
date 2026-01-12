/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Service for building DSIF Federation Manifest and Connection Profile.
 */
public class DsifManifestService {

    private static final List<Object> JSON_LD_CONTEXT = Arrays.asList(
        "https://www.w3.org/ns/dcat3.jsonld",
        Map.of(
            "dsif", "https://spec.dsif.dev/ns#",
            "dct", "http://purl.org/dc/terms/",
            "schema", "https://schema.org/"
        )
    );

    private static final List<Map<String, String>> CONFORMS_TO = Arrays.asList(
        Map.of("@id", "https://www.w3.org/TR/vocab-dcat-3/"),
        Map.of("@id", "https://extensions.ckan.org/extension/dcat/"),
        Map.of("@id", "https://spec.dsif.dev/schemas/manifest/0.1/ic-manifest.schema.json"),
        Map.of("@id", "https://spec.dsif.dev/profiles/search/0.1")
    );

    private static final List<String> SEARCH_PARAMETERS = Arrays.asList(
        "q", "keyword", "type", "format", "publisher", "page", "pageSize", "sort"
    );

    private final DsifConfiguration config;

    public DsifManifestService(DsifConfiguration config) {
        this.config = config;
    }

    /**
     * Build the Federation Manifest (IC Manifest) for this catalog.
     */
    public FederationManifest buildManifest() {
        return FederationManifest.builder()
            .context(JSON_LD_CONTEXT)
            .id(config.getManifestUrl())
            .title(config.getCatalogTitle() + " - Manifest")
            .description(config.getCatalogDescription())
            .publisher(new Publisher(config.getPublisherId(), config.getPublisherName()))
            .conformsTo(CONFORMS_TO)
            .sector(config.getCatalogSector())
            .connectionProfile(new ConnectionProfileRef(config.getConnectionProfileUrl()))
            .ttlSeconds(config.getTtlSeconds())
            .dsifVersion(config.getDsifVersion())
            .contactPoint(buildContactPoint())
            .build();
    }

    /**
     * Build the Connection Profile for SA registration.
     */
    public ConnectionProfile buildConnectionProfile() {
        return ConnectionProfile.builder()
            .context(Arrays.asList(Map.of(
                "dsif", "https://spec.dsif.dev/ns#",
                "dct", "http://purl.org/dc/terms/"
            )))
            .id(config.getConnectionProfileUrl())
            .title(config.getCatalogTitle() + " - Connection Profile")
            .conformsTo(Arrays.asList(Map.of("@id", "https://spec.dsif.dev/profiles/search/0.1")))
            .endpoints(buildEndpoints())
            .searchTemplate(config.getSearchTemplate())
            .searchParameters(SEARCH_PARAMETERS)
            .auth(new AuthConfig(config.getAuthScheme()))
            .ttlSeconds(config.getTtlSeconds())
            .build();
    }

    private EndpointSet buildEndpoints() {
        return new EndpointSet(
            config.getCatalogUrl(),
            config.getDatasetUrlTemplate(),
            config.getSearchUrl(),
            config.getHealthUrl()
        );
    }

    private ContactPoint buildContactPoint() {
        String name = config.getContactName();
        String email = config.getContactEmail();

        if (name.isEmpty() && email.isEmpty()) {
            return null;
        }

        return new ContactPoint(
            config.getContactType(),
            name.isEmpty() ? null : name,
            email.isEmpty() ? null : email
        );
    }
}
