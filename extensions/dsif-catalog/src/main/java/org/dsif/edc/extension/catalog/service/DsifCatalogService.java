/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.policy.model.Rule;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for building DCAT catalogs using EDC's existing DatasetResolver.
 *
 * This service wraps EDC's catalog infrastructure and adds:
 * - Hydra pagination (DSIF requirement)
 * - DSIF provenance fields (originCatalogId, hopCount, federationPath)
 * - Asset metadata (description, author, content type, rating)
 * - ODRL policy information from ContractDefinitionStore/PolicyDefinitionStore
 * - DSP negotiation endpoint metadata
 *
 * When no participant context is provided (e.g., anonymous catalog browsing),
 * falls back to using AssetIndex directly to return all available assets.
 */
public class DsifCatalogService {

    private static final String DCAT_NAMESPACE = "http://www.w3.org/ns/dcat#";
    private static final String DCT_NAMESPACE = "http://purl.org/dc/terms/";
    private static final String HYDRA_NAMESPACE = "http://www.w3.org/ns/hydra/core#";
    private static final String DSIF_NAMESPACE = "https://spec.dsif.dev/ns#";
    private static final String ODRL_NAMESPACE = "http://www.w3.org/ns/odrl/2/";
    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String DSPACE_NAMESPACE = "https://w3id.org/dspace/v0.8/";

    private static final String EDC_ID_PROPERTY = "https://w3id.org/edc/v0.0.1/ns/id";

    private final DsifConfiguration config;
    private final DatasetResolver datasetResolver;
    private final AssetIndex assetIndex;
    private final ContractDefinitionStore contractDefinitionStore;
    private final PolicyDefinitionStore policyDefinitionStore;

    public DsifCatalogService(DsifConfiguration config, DatasetResolver datasetResolver, AssetIndex assetIndex,
                               ContractDefinitionStore contractDefinitionStore, PolicyDefinitionStore policyDefinitionStore) {
        this.config = config;
        this.datasetResolver = datasetResolver;
        this.assetIndex = assetIndex;
        this.contractDefinitionStore = contractDefinitionStore;
        this.policyDefinitionStore = policyDefinitionStore;
    }

    /**
     * Get paginated DCAT catalog with Hydra pagination.
     * Uses EDC's DatasetResolver if participant context is available,
     * otherwise falls back to AssetIndex for anonymous access.
     *
     * @param page Page number (1-based)
     * @param participantContext The participant context (can be null)
     * @param participantAgent The requesting agent (can be null)
     * @param protocol The protocol version
     * @return JSON-LD catalog with Hydra pagination
     */
    public JsonObject getCatalog(int page, ParticipantContext participantContext,
                                  ParticipantAgent participantAgent, String protocol) {
        int pageSize = config.getSearchDefaultPageSize();
        int offset = (page - 1) * pageSize;

        // Build QuerySpec using EDC's existing infrastructure
        QuerySpec querySpec = QuerySpec.Builder.newInstance()
                .offset(offset)
                .limit(pageSize)
                .build();

        List<Dataset> datasets;
        long totalItems;

        // Try DatasetResolver first if participant context is available
        if (participantContext != null && participantAgent != null) {
            datasets = datasetResolver.query(participantContext, participantAgent, querySpec, protocol)
                    .collect(Collectors.toList());
            totalItems = datasetResolver.query(participantContext, participantAgent, QuerySpec.max(), protocol)
                    .count();
        } else {
            // Fallback: use AssetIndex directly for anonymous access
            datasets = queryAssetsAsDatasets(querySpec);
            totalItems = assetIndex.queryAssets(QuerySpec.max()).count();
        }

        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        // Build contract index for enrichment
        Map<String, ContractDefinition> contractIndex = buildContractIndex();

        // Build DCAT catalog JSON-LD with Hydra pagination
        return buildCatalogJsonLd(datasets, page, pageSize, (int) totalItems, totalPages, contractIndex);
    }

    /**
     * Query assets and convert them to Dataset objects for catalog display.
     * Used as fallback when no participant context is available.
     */
    private List<Dataset> queryAssetsAsDatasets(QuerySpec querySpec) {
        return assetIndex.queryAssets(querySpec)
                .map(this::assetToDataset)
                .collect(Collectors.toList());
    }

    /**
     * Convert an Asset to a minimal Dataset for catalog display.
     */
    private Dataset assetToDataset(Asset asset) {
        return Dataset.Builder.newInstance()
                .id(asset.getId())
                .properties(asset.getProperties())
                .build();
    }

    /**
     * Get a single dataset by ID.
     * Uses EDC's DatasetResolver if participant context is available,
     * otherwise falls back to AssetIndex.
     *
     * @param id Dataset/Asset ID
     * @param participantContext The participant context (can be null)
     * @param participantAgent The requesting agent (can be null)
     * @param protocol The protocol version
     * @return Dataset or null if not found
     */
    public Dataset getDataset(String id, ParticipantContext participantContext,
                               ParticipantAgent participantAgent, String protocol) {
        // Try DatasetResolver first if participant context is available
        if (participantContext != null && participantAgent != null) {
            Dataset dataset = datasetResolver.getById(participantContext, participantAgent, id, protocol);
            if (dataset != null) {
                return dataset;
            }
        }

        // Fallback: use AssetIndex directly
        Asset asset = assetIndex.findById(id);
        if (asset != null) {
            return assetToDataset(asset);
        }
        return null;
    }

    /**
     * Get a single dataset as JSON-LD with DSIF provenance.
     */
    public JsonObject getDatasetJsonLd(String id, ParticipantContext participantContext,
                                        ParticipantAgent participantAgent, String protocol) {
        Dataset dataset = getDataset(id, participantContext, participantAgent, protocol);
        if (dataset == null) {
            return null;
        }
        Map<String, ContractDefinition> contractIndex = buildContractIndex();
        return buildDatasetJsonLd(dataset, contractIndex);
    }

    /**
     * Build an index mapping asset IDs to their contract definitions.
     * Queries all contract definitions and extracts the target asset ID
     * from the assetsSelector criteria.
     */
    private Map<String, ContractDefinition> buildContractIndex() {
        Map<String, ContractDefinition> index = new HashMap<>();
        contractDefinitionStore.findAll(QuerySpec.max()).forEach(cd -> {
            String assetId = extractAssetIdFromSelector(cd.getAssetsSelector());
            if (assetId != null) {
                index.put(assetId, cd);
            }
        });
        return index;
    }

    /**
     * Extract the target asset ID from a contract definition's assetsSelector.
     * Looks for a criterion with operandLeft = EDC ID property and operator = "=".
     */
    private String extractAssetIdFromSelector(List<Criterion> criteria) {
        if (criteria == null) {
            return null;
        }
        for (Criterion criterion : criteria) {
            Object left = criterion.getOperandLeft();
            if (EDC_ID_PROPERTY.equals(String.valueOf(left)) && "=".equals(criterion.getOperator())) {
                return String.valueOf(criterion.getOperandRight());
            }
        }
        return null;
    }

    private JsonObject buildCatalogJsonLd(List<Dataset> datasets, int page, int pageSize,
                                           int totalItems, int totalPages,
                                           Map<String, ContractDefinition> contractIndex) {
        var factory = Json.createBuilderFactory(Map.of());

        // Build datasets array with DSIF provenance
        var datasetsArray = Json.createArrayBuilder();
        for (Dataset dataset : datasets) {
            datasetsArray.add(buildDatasetJsonLd(dataset, contractIndex));
        }

        // Build Hydra pagination view
        JsonObject hydraView = buildHydraView(factory, page, pageSize, totalItems, totalPages);

        // Build catalog
        return factory.createObjectBuilder()
                .add("@context", buildContext(factory))
                .add("@id", config.getCatalogUrl() + "?page=" + page)
                .add("@type", "dcat:Catalog")
                .add("dct:title", config.getCatalogTitle())
                .add("dct:description", config.getCatalogDescription())
                .add("dct:publisher", factory.createObjectBuilder()
                        .add("@id", config.getPublisherId())
                        .add("dct:title", config.getPublisherName()))
                .add("dcat:dataset", datasetsArray)
                .add("hydra:view", hydraView)
                .build();
    }

    private JsonObject buildDatasetJsonLd(Dataset dataset, Map<String, ContractDefinition> contractIndex) {
        var factory = Json.createBuilderFactory(Map.of());
        JsonObjectBuilder builder = factory.createObjectBuilder()
                .add("@id", buildDatasetUri(dataset.getId()))
                .add("@type", determineDatasetType(dataset));

        // Add properties from dataset
        Map<String, Object> props = dataset.getProperties();

        // Title
        String title = getProp(props, "name");
        if (title == null) {
            title = getProp(props, "dct:title");
        }
        builder.add("dct:title", title != null ? title : dataset.getId());

        // Description
        String description = getProp(props, "description");
        if (description != null) {
            builder.add("dct:description", description);
        }

        // Content type
        String contentType = getProp(props, "contenttype");
        if (contentType != null) {
            builder.add("dcat:mediaType", contentType);
        }

        // DSP Asset ID (the raw ID for contract negotiation)
        builder.add("dspace:assetId", dataset.getId());

        // Author / Creator
        String author = getProp(props, "author");
        if (author != null) {
            builder.add("dct:creator", author);
        }

        // Rating (DSIF-specific)
        String rating = getProp(props, "rating");
        if (rating != null) {
            builder.add("dsif:rating", rating);
        }

        // Pricing (DSIF-specific) - extract from asset properties
        Object pricing = getPropertyValue(props, "dsif:pricing");
        if (pricing == null) {
            pricing = props.get("https://spec.dsif.dev/ns#pricing");
        }
        if (pricing instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pricingMap = (Map<String, Object>) pricing;
            var pricingBuilder = factory.createObjectBuilder();

            // Extract pricing fields (unwrap JSON-LD expanded values)
            Object cost = getPricingField(pricingMap, "costPerAPICall");
            if (cost != null) {
                Object unwrapped = unwrapJsonLdValue(cost);
                if (unwrapped instanceof Number) {
                    pricingBuilder.add("dsif:costPerAPICall", ((Number) unwrapped).doubleValue());
                } else {
                    try {
                        pricingBuilder.add("dsif:costPerAPICall", Double.parseDouble(String.valueOf(unwrapped)));
                    } catch (NumberFormatException e) {
                        pricingBuilder.add("dsif:costPerAPICall", String.valueOf(unwrapped));
                    }
                }
            }

            Object fee = getPricingField(pricingMap, "setupFee");
            if (fee != null) {
                Object unwrapped = unwrapJsonLdValue(fee);
                if (unwrapped instanceof Number) {
                    pricingBuilder.add("dsif:setupFee", ((Number) unwrapped).doubleValue());
                } else {
                    try {
                        pricingBuilder.add("dsif:setupFee", Double.parseDouble(String.valueOf(unwrapped)));
                    } catch (NumberFormatException e) {
                        pricingBuilder.add("dsif:setupFee", String.valueOf(unwrapped));
                    }
                }
            }

            Object period = getPricingField(pricingMap, "billingPeriod");
            if (period != null) {
                pricingBuilder.add("dsif:billingPeriod", String.valueOf(unwrapJsonLdValue(period)));
            }

            Object currency = getPricingField(pricingMap, "currency");
            if (currency != null) {
                pricingBuilder.add("dsif:currency", String.valueOf(unwrapJsonLdValue(currency)));
            }

            Object desc = getPricingField(pricingMap, "description");
            if (desc != null) {
                pricingBuilder.add("dsif:description", String.valueOf(unwrapJsonLdValue(desc)));
            }

            builder.add("dsif:pricing", pricingBuilder);
        }

        // Preview links (DSIF-specific) - links to sample/preview content
        Object previewLinks = getPropertyValue(props, "previewLinks");
        if (previewLinks == null) {
            previewLinks = props.get("dsif:previewLinks");
        }
        if (previewLinks == null) {
            previewLinks = props.get(DSIF_NAMESPACE + "previewLinks");
        }
        if (previewLinks != null) {
            Object unwrapped = unwrapJsonLdValue(previewLinks);
            String linksStr = String.valueOf(unwrapped);
            // Split comma-separated links into array
            String[] links = linksStr.split("\\s*,\\s*");
            if (links.length > 0) {
                var linksArray = Json.createArrayBuilder();
                for (String link : links) {
                    String trimmed = link.trim();
                    if (!trimmed.isEmpty()) {
                        linksArray.add(factory.createObjectBuilder().add("@id", trimmed));
                    }
                }
                builder.add("dsif:previewLinks", linksArray);
            }
        }

        // Keywords
        Object keywords = getPropertyValue(props, "keywords");
        if (keywords == null) {
            keywords = props.get("dcat:keyword");
        }
        if (keywords != null) {
            if (keywords instanceof List) {
                var keywordArray = Json.createArrayBuilder();
                for (Object kw : (List<?>) keywords) {
                    keywordArray.add(String.valueOf(kw));
                }
                builder.add("dcat:keyword", keywordArray);
            } else {
                builder.add("dcat:keyword", Json.createArrayBuilder().add(String.valueOf(keywords)));
            }
        }

        // Distributions (from EDC's Dataset model)
        if (dataset.getDistributions() != null && !dataset.getDistributions().isEmpty()) {
            var distArray = Json.createArrayBuilder();
            for (var dist : dataset.getDistributions()) {
                var distBuilder = factory.createObjectBuilder()
                        .add("@type", "dcat:Distribution");
                if (dist.getFormat() != null) {
                    distBuilder.add("dcat:mediaType", dist.getFormat());
                }
                if (dist.getDataService() != null && dist.getDataService().getEndpointUrl() != null) {
                    distBuilder.add("dcat:accessURL", factory.createObjectBuilder()
                            .add("@id", dist.getDataService().getEndpointUrl()));
                }
                distArray.add(distBuilder);
            }
            builder.add("dcat:distribution", distArray);
        }

        // Contract and policy information
        ContractDefinition contractDef = contractIndex.get(dataset.getId());
        if (contractDef != null) {
            // ODRL policy
            PolicyDefinition policyDef = policyDefinitionStore.findById(contractDef.getContractPolicyId());
            if (policyDef != null) {
                builder.add("odrl:hasPolicy", buildOdrlPolicy(policyDef.getPolicy(), factory));
            }

            // Contract definition ID
            builder.add("edc:contractDefinitionId", contractDef.getId());
        }

        // Negotiation metadata
        builder.add("dsif:negotiation", factory.createObjectBuilder()
                .add("dspace:participantId", config.getParticipantId())
                .add("dspace:protocolEndpoint", config.getProtocolEndpoint()));

        // DSIF provenance (for IC, local data has hopCount=0)
        builder.add("dsif:originCatalogId", config.getCatalogId());
        builder.add("dsif:federationPath", Json.createArrayBuilder());
        builder.add("dsif:hopCount", 0);

        return builder.build();
    }

    /**
     * Build an ODRL policy JSON-LD object from an EDC Policy model.
     */
    private JsonObject buildOdrlPolicy(Policy policy, jakarta.json.JsonBuilderFactory factory) {
        JsonObjectBuilder policyBuilder = factory.createObjectBuilder()
                .add("@type", "odrl:Set");

        // Permissions
        JsonArrayBuilder permissions = Json.createArrayBuilder();
        for (Permission perm : policy.getPermissions()) {
            permissions.add(buildOdrlRule(perm, factory));
        }
        policyBuilder.add("odrl:permission", permissions);

        // Prohibitions
        JsonArrayBuilder prohibitions = Json.createArrayBuilder();
        for (Prohibition prohibition : policy.getProhibitions()) {
            prohibitions.add(buildOdrlRule(prohibition, factory));
        }
        policyBuilder.add("odrl:prohibition", prohibitions);

        // Obligations
        JsonArrayBuilder obligations = Json.createArrayBuilder();
        for (Duty duty : policy.getObligations()) {
            obligations.add(buildOdrlRule(duty, factory));
        }
        policyBuilder.add("odrl:obligation", obligations);

        return policyBuilder.build();
    }

    /**
     * Build a JSON-LD representation of an ODRL rule (permission, prohibition, or duty).
     */
    private JsonObject buildOdrlRule(Rule rule, jakarta.json.JsonBuilderFactory factory) {
        JsonObjectBuilder ruleBuilder = factory.createObjectBuilder();
        if (rule.getAction() != null && rule.getAction().getType() != null) {
            ruleBuilder.add("odrl:action", rule.getAction().getType());
        }
        return ruleBuilder.build();
    }

    private JsonObject buildHydraView(jakarta.json.JsonBuilderFactory factory,
                                       int page, int pageSize, int totalItems, int totalPages) {
        String baseUrl = config.getCatalogUrl();

        JsonObjectBuilder viewBuilder = factory.createObjectBuilder()
                .add("@id", baseUrl + "?page=" + page)
                .add("@type", "hydra:PartialCollectionView")
                .add("hydra:totalItems", totalItems)
                .add("hydra:first", baseUrl + "?page=1");

        if (totalPages > 0) {
            viewBuilder.add("hydra:last", baseUrl + "?page=" + totalPages);
        }

        if (page > 1) {
            viewBuilder.add("hydra:previous", baseUrl + "?page=" + (page - 1));
        }

        if (page < totalPages) {
            viewBuilder.add("hydra:next", baseUrl + "?page=" + (page + 1));
        }

        return viewBuilder.build();
    }

    private JsonObject buildContext(jakarta.json.JsonBuilderFactory factory) {
        return factory.createObjectBuilder()
                .add("dcat", DCAT_NAMESPACE)
                .add("dct", DCT_NAMESPACE)
                .add("hydra", HYDRA_NAMESPACE)
                .add("dsif", DSIF_NAMESPACE)
                .add("odrl", ODRL_NAMESPACE)
                .add("edc", EDC_NAMESPACE)
                .add("dspace", DSPACE_NAMESPACE)
                .build();
    }

    private String buildDatasetUri(String assetId) {
        return String.format("urn:dsif:%s:%s", config.getCatalogId(), assetId);
    }

    private String determineDatasetType(Dataset dataset) {
        String type = getProp(dataset.getProperties(), "type");
        if (type != null) {
            String typeStr = type.toLowerCase();
            if ("service".equals(typeStr) || "dataservice".equals(typeStr)) {
                return "dcat:DataService";
            }
        }
        return "dcat:Dataset";
    }

    /**
     * Look up a property value by short key, trying multiple key formats.
     * EDC stores properties with full namespace keys (e.g., "https://w3id.org/edc/v0.0.1/ns/name")
     * but they may also appear as short keys ("name") depending on the source.
     *
     * @return the string value or null if not found
     */
    private String getProp(Map<String, Object> props, String key) {
        Object value = getPropertyValue(props, key);
        return value != null ? value.toString() : null;
    }

    /**
     * Look up a raw property value by short key, trying multiple key formats.
     */
    private Object getPropertyValue(Map<String, Object> props, String key) {
        // Try plain key first
        Object value = props.get(key);
        if (value != null) {
            return value;
        }
        // Try EDC full namespace
        value = props.get(EDC_NAMESPACE + key);
        if (value != null) {
            return value;
        }
        // Try edc: prefix
        value = props.get("edc:" + key);
        if (value != null) {
            return value;
        }
        // Try legacy asset:prop: prefix
        return props.get("asset:prop:" + key);
    }

    /**
     * Get a pricing field value from a pricing map, trying both short and full namespace keys.
     */
    private Object getPricingField(Map<String, Object> pricingMap, String fieldName) {
        Object value = pricingMap.get("dsif:" + fieldName);
        if (value != null) return value;
        value = pricingMap.get(DSIF_NAMESPACE + fieldName);
        if (value != null) return value;
        return pricingMap.get(fieldName);
    }

    /**
     * Unwrap JSON-LD expanded value format.
     * JSON-LD expansion can produce values like:
     * - [{@value=0.1}] (list with value object)
     * - {value=0.1} (map with @value key)
     * - 0.1 (plain value)
     *
     * @param value the potentially wrapped value
     * @return the unwrapped value
     */
    @SuppressWarnings("unchecked")
    private Object unwrapJsonLdValue(Object value) {
        if (value == null) {
            return null;
        }
        // Handle list wrapper: [{@value=X}]
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty()) {
                return unwrapJsonLdValue(list.get(0));
            }
            return null;
        }
        // Handle map wrapper: {@value=X}
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey("@value")) {
                return map.get("@value");
            }
            // EDC sometimes uses "value" without @
            if (map.containsKey("value")) {
                return map.get("value");
            }
        }
        // Return as-is if already a plain value
        return value;
    }
}
