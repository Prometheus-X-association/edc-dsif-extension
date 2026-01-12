/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.service;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetResolver;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for building DCAT catalogs using EDC's existing DatasetResolver.
 *
 * This service wraps EDC's catalog infrastructure and adds:
 * - Hydra pagination (DSIF requirement)
 * - DSIF provenance fields (originCatalogId, hopCount, federationPath)
 *
 * When no participant context is provided (e.g., anonymous catalog browsing),
 * falls back to using AssetIndex directly to return all available assets.
 */
public class DsifCatalogService {

    private static final String DCAT_NAMESPACE = "http://www.w3.org/ns/dcat#";
    private static final String DCT_NAMESPACE = "http://purl.org/dc/terms/";
    private static final String HYDRA_NAMESPACE = "http://www.w3.org/ns/hydra/core#";
    private static final String DSIF_NAMESPACE = "https://spec.dsif.dev/ns#";

    private final DsifConfiguration config;
    private final DatasetResolver datasetResolver;
    private final AssetIndex assetIndex;

    public DsifCatalogService(DsifConfiguration config, DatasetResolver datasetResolver, AssetIndex assetIndex) {
        this.config = config;
        this.datasetResolver = datasetResolver;
        this.assetIndex = assetIndex;
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

        // Build DCAT catalog JSON-LD with Hydra pagination
        return buildCatalogJsonLd(datasets, page, pageSize, (int) totalItems, totalPages);
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
        return buildDatasetJsonLd(dataset);
    }

    private JsonObject buildCatalogJsonLd(List<Dataset> datasets, int page, int pageSize,
                                           int totalItems, int totalPages) {
        var factory = Json.createBuilderFactory(Map.of());

        // Build datasets array with DSIF provenance
        var datasetsArray = Json.createArrayBuilder();
        for (Dataset dataset : datasets) {
            datasetsArray.add(buildDatasetJsonLd(dataset));
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

    private JsonObject buildDatasetJsonLd(Dataset dataset) {
        var factory = Json.createBuilderFactory(Map.of());
        JsonObjectBuilder builder = factory.createObjectBuilder()
                .add("@id", buildDatasetUri(dataset.getId()))
                .add("@type", determineDatasetType(dataset));

        // Add properties from dataset
        Map<String, Object> props = dataset.getProperties();

        // Title
        Object title = props.getOrDefault("name", props.getOrDefault("dct:title", dataset.getId()));
        builder.add("dct:title", String.valueOf(title));

        // Description
        Object description = props.get("description");
        if (description == null) {
            description = props.get("dct:description");
        }
        if (description != null) {
            builder.add("dct:description", String.valueOf(description));
        }

        // Keywords
        Object keywords = props.get("keywords");
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

        // DSIF provenance (for IC, local data has hopCount=0)
        builder.add("dsif:originCatalogId", config.getCatalogId());
        builder.add("dsif:federationPath", Json.createArrayBuilder());
        builder.add("dsif:hopCount", 0);

        return builder.build();
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
                .build();
    }

    private String buildDatasetUri(String assetId) {
        return String.format("urn:dsif:%s:%s", config.getCatalogId(), assetId);
    }

    private String determineDatasetType(Dataset dataset) {
        Object type = dataset.getProperties().get("type");
        if (type != null) {
            String typeStr = String.valueOf(type).toLowerCase();
            if ("service".equals(typeStr) || "dataservice".equals(typeStr)) {
                return "dcat:DataService";
            }
        }
        return "dcat:Dataset";
    }
}
