/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog;

import org.dsif.edc.extension.catalog.controller.*;
import org.dsif.edc.extension.catalog.service.*;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

/**
 * DSIF Independent Catalog Extension for Eclipse Dataspace Connector.
 *
 * Implements the DSIF PoC Catalogue Federation specification v0.2
 * for Independent Catalogs (IC).
 *
 * This extension leverages EDC's existing catalog infrastructure:
 * - Uses DatasetResolver for Asset → Dataset transformation
 * - Uses AssetIndex with QuerySpec for search filtering
 * - Adds DSIF-specific endpoints and provenance information
 *
 * Provides the following endpoints:
 * - GET /.well-known/dataspace-catalog.json - Federation Manifest
 * - GET /.well-known/connection-profile - Connection Profile
 * - GET /dsif/health - Health check
 * - GET /dsif/catalog.jsonld - DCAT catalog with Hydra pagination
 * - GET /dsif/dataset/{id}.jsonld - Individual dataset DCAT
 * - GET /dsif/search - Neutral Search
 *
 * @see <a href="https://gitlab.com/prometheus-x/data-space-interoperability-framework/-/wikis/PoC-Catalogue-Federation">DSIF Specification</a>
 */
@Extension(value = DsifCatalogExtension.NAME)
public class DsifCatalogExtension implements ServiceExtension {

    public static final String NAME = "DSIF Catalog Extension";
    public static final String VERSION = "0.1.0";

    @Inject
    private WebService webService;

    @Inject
    private AssetIndex assetIndex;

    @Inject
    private DatasetResolver datasetResolver;

    private DsifConfiguration config;
    private DsifHealthService healthService;
    private SectorAuthorityRegistrationService registrationService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        monitor.info("Initializing " + NAME + " v" + VERSION);

        // Initialize configuration
        config = new DsifConfiguration(context);

        // Log configuration
        monitor.info("DSIF Catalog ID: " + config.getCatalogId());
        monitor.info("DSIF Catalog Sector: " + config.getCatalogSector());
        monitor.info("DSIF Base URL: " + config.getBaseUrl());

        // Initialize services
        // - DsifManifestService: builds DSIF-specific manifests
        // - DsifHealthService: health monitoring
        // - DsifCatalogService: wraps DatasetResolver, falls back to AssetIndex for anonymous access
        // - DsifSearchService: uses AssetIndex with QuerySpec, transforms to DSIF format
        var manifestService = new DsifManifestService(config);
        healthService = new DsifHealthService(config, VERSION);
        var catalogService = new DsifCatalogService(config, datasetResolver, assetIndex);
        var searchService = new DsifSearchService(config, assetIndex);

        // Initialize SA registration service
        registrationService = new SectorAuthorityRegistrationService(config, monitor);

        // Register controllers
        registerControllers(context, manifestService, catalogService, searchService);

        monitor.info(NAME + " initialized successfully");
        monitor.info("  - Manifest: " + config.getManifestUrl());
        monitor.info("  - Health: " + config.getHealthUrl());
        monitor.info("  - Catalog: " + config.getCatalogUrl());
        monitor.info("  - Search: " + config.getSearchUrl());
    }

    private void registerControllers(ServiceExtensionContext context,
                                     DsifManifestService manifestService,
                                     DsifCatalogService catalogService,
                                     DsifSearchService searchService) {
        // Register .well-known endpoints
        var wellKnownController = new WellKnownController(manifestService, config);
        webService.registerResource(wellKnownController);

        // Register health endpoint
        var healthController = new DsifHealthController(healthService, config);
        webService.registerResource(healthController);

        // Register catalog endpoints (uses DatasetResolver internally)
        var catalogController = new DsifCatalogController(catalogService, config, context);
        webService.registerResource(catalogController);

        // Register search endpoint (uses AssetIndex with QuerySpec)
        var searchController = new DsifSearchController(searchService, config);
        webService.registerResource(searchController);
    }

    @Override
    public void start() {
        // Register with Sector Authority if configured
        String saUrl = config.getSectorAuthorityUrl();
        if (saUrl == null || saUrl.isBlank()) {
            // No SA URL configured - standalone mode
        } else {
            registrationService.register();
            registrationService.verifyRegistration();
        }
    }

    @Override
    public void shutdown() {
        // Cleanup if needed
    }
}
