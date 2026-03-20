/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog;

import org.dsif.edc.extension.catalog.controller.*;
import org.dsif.edc.extension.catalog.policy.DsifPricingConstraintFunction;
import org.dsif.edc.extension.catalog.policy.OdrlDateTimeConstraintFunction;
import org.dsif.edc.extension.catalog.policy.OdrlPassthroughConstraintFunction;
import org.dsif.edc.extension.catalog.service.*;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Prohibition;

import java.util.List;
import java.util.Set;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

import static org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext.CATALOG_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;

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

    @Inject
    private ContractDefinitionStore contractDefinitionStore;

    @Inject
    private PolicyDefinitionStore policyDefinitionStore;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

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

        // Register DSIF policy functions (pricing + ODRL dateTime)
        registerDsifPolicyFunctions();

        // Initialize services
        // - DsifManifestService: builds DSIF-specific manifests
        // - DsifHealthService: health monitoring
        // - DsifCatalogService: wraps DatasetResolver, falls back to AssetIndex for anonymous access
        // - DsifSearchService: uses AssetIndex with QuerySpec, transforms to DSIF format
        var manifestService = new DsifManifestService(config);
        healthService = new DsifHealthService(config, VERSION);
        var catalogService = new DsifCatalogService(config, datasetResolver, assetIndex, contractDefinitionStore, policyDefinitionStore);
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

    private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";

    private void registerDsifPolicyFunctions() {
        // --- ODRL actions: bind all common actions to catalog and negotiation scopes ---
        var odrlActions = List.of("compensate", "distribute", "commercialize", "delete");
        for (var action : odrlActions) {
            ruleBindingRegistry.bind("odrl:" + action, CATALOG_SCOPE);
            ruleBindingRegistry.bind("odrl:" + action, NEGOTIATION_SCOPE);
            ruleBindingRegistry.bind(ODRL_NS + action, CATALOG_SCOPE);
            ruleBindingRegistry.bind(ODRL_NS + action, NEGOTIATION_SCOPE);
        }

        // Dynamic scope binding for dsif: and odrl: namespace leftOperands
        ruleBindingRegistry.dynamicBind(key -> {
            if (key.startsWith(DsifPricingConstraintFunction.DSIF_NAMESPACE)
                    || key.startsWith(DsifPricingConstraintFunction.DSIF_COMPACT_PREFIX)
                    || key.startsWith(ODRL_NS)
                    || key.startsWith("odrl:")) {
                return Set.of(CATALOG_SCOPE, NEGOTIATION_SCOPE);
            }
            return Set.of();
        });

        // --- DSIF Pricing (descriptive, always-pass) ---
        // Register dynamic function for all dsif: namespace constraints (always passes)
        policyEngine.registerFunction(CatalogPolicyContext.class, Duty.class, new DsifPricingConstraintFunction<>());
        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Duty.class, new DsifPricingConstraintFunction<>());

        // --- ODRL descriptive constraints (always-pass for odrl: namespace leftOperands like odrl:event) ---
        policyEngine.registerFunction(CatalogPolicyContext.class, Duty.class, new OdrlPassthroughConstraintFunction<>());
        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Duty.class, new OdrlPassthroughConstraintFunction<>());
        policyEngine.registerFunction(CatalogPolicyContext.class, Prohibition.class, new OdrlPassthroughConstraintFunction<>());
        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Prohibition.class, new OdrlPassthroughConstraintFunction<>());

        // --- ODRL dateTime (enforced, compares current time to ISO-8601 bound) ---
        var dateTimeKey = OdrlDateTimeConstraintFunction.ODRL_DATETIME_KEY;
        ruleBindingRegistry.bind(dateTimeKey, CATALOG_SCOPE);
        ruleBindingRegistry.bind(dateTimeKey, NEGOTIATION_SCOPE);
        policyEngine.registerFunction(CatalogPolicyContext.class, Permission.class, dateTimeKey, new OdrlDateTimeConstraintFunction<>());
        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, dateTimeKey, new OdrlDateTimeConstraintFunction<>());
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
