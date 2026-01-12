/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.controller;

import jakarta.json.JsonObject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.ProblemDetail;
import org.dsif.edc.extension.catalog.service.DsifCatalogService;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Map;

/**
 * Controller for DSIF DCAT catalog endpoints.
 *
 * Uses EDC's DatasetResolver internally to fetch datasets,
 * adding DSIF-specific Hydra pagination and provenance fields.
 */
@Path("/dsif")
public class DsifCatalogController {

    public static final String CONTENT_TYPE_JSON_LD = "application/ld+json";
    public static final String CONTENT_TYPE_PROBLEM_JSON = "application/problem+json";
    public static final String DSIF_VERSION_HEADER = "X-DSIF-Version";

    // Default protocol for DSIF (DSP 2025/1)
    private static final String DEFAULT_PROTOCOL = "dataspace-protocol-http:2025/1";

    private final DsifCatalogService catalogService;
    private final DsifConfiguration config;
    private final ServiceExtensionContext extensionContext;

    public DsifCatalogController(DsifCatalogService catalogService,
                                  DsifConfiguration config,
                                  ServiceExtensionContext extensionContext) {
        this.catalogService = catalogService;
        this.config = config;
        this.extensionContext = extensionContext;
    }

    /**
     * GET /dsif/catalog.jsonld
     *
     * Returns DCAT catalog with Hydra pagination.
     * Uses EDC's DatasetResolver for Asset → Dataset transformation.
     *
     * @param page Page number (1-based, default 1)
     * @param modifiedSince Filter by modification date (ISO 8601)
     * @param profiles DCAT profiles to conform to
     * @param q Full-text search query
     * @param fq Filter query (CKAN-style)
     */
    @GET
    @Path("/catalog.jsonld")
    @Produces(CONTENT_TYPE_JSON_LD)
    public Response getCatalog(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("modified_since") String modifiedSince,
            @QueryParam("profiles") String profiles,
            @QueryParam("q") String q,
            @QueryParam("fq") String fq) {

        // Validate page parameter
        if (page < 1) {
            return problemResponse(
                ProblemDetail.invalidPage("/dsif/catalog.jsonld?page=" + page),
                Response.Status.BAD_REQUEST
            );
        }

        try {
            // Get participant context and agent for DatasetResolver
            ParticipantContext participantContext = getParticipantContext();
            ParticipantAgent participantAgent = getAnonymousAgent();

            // Use DsifCatalogService which wraps DatasetResolver
            JsonObject catalog = catalogService.getCatalog(
                page, participantContext, participantAgent, DEFAULT_PROTOCOL
            );

            return Response.ok(catalog.toString())
                .type(CONTENT_TYPE_JSON_LD)
                .header(DSIF_VERSION_HEADER, config.getDsifVersion())
                .header("Cache-Control", "public, max-age=" + config.getTtlSeconds())
                .build();

        } catch (Exception e) {
            return problemResponse(
                ProblemDetail.internalError(
                    "Failed to retrieve catalog: " + e.getMessage(),
                    "/dsif/catalog.jsonld",
                    "CATALOG_ERROR"
                ),
                Response.Status.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * GET /dsif/dataset/{id}.jsonld
     *
     * Returns individual dataset DCAT JSON-LD.
     * Uses EDC's DatasetResolver.getById() internally.
     *
     * @param id Dataset/Asset ID
     */
    @GET
    @Path("/dataset/{id}.jsonld")
    @Produces(CONTENT_TYPE_JSON_LD)
    public Response getDataset(@PathParam("id") String id) {
        if (id == null || id.isEmpty()) {
            return problemResponse(
                ProblemDetail.badRequest(
                    "Dataset ID is required",
                    "/dsif/dataset/.jsonld",
                    "MISSING_ID"
                ),
                Response.Status.BAD_REQUEST
            );
        }

        try {
            // Get participant context and agent
            ParticipantContext participantContext = getParticipantContext();
            ParticipantAgent participantAgent = getAnonymousAgent();

            JsonObject dataset = catalogService.getDatasetJsonLd(
                id, participantContext, participantAgent, DEFAULT_PROTOCOL
            );

            if (dataset == null) {
                return problemResponse(
                    ProblemDetail.datasetNotFound(id, "/dsif/dataset/" + id + ".jsonld"),
                    Response.Status.NOT_FOUND
                );
            }

            return Response.ok(dataset.toString())
                .type(CONTENT_TYPE_JSON_LD)
                .header(DSIF_VERSION_HEADER, config.getDsifVersion())
                .header("Cache-Control", "public, max-age=" + config.getTtlSeconds())
                .build();

        } catch (Exception e) {
            return problemResponse(
                ProblemDetail.internalError(
                    "Failed to retrieve dataset: " + e.getMessage(),
                    "/dsif/dataset/" + id + ".jsonld",
                    "DATASET_ERROR"
                ),
                Response.Status.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Create a default participant context for DSIF endpoints.
     * DSIF endpoints are public, so we use the connector's default context.
     */
    private ParticipantContext getParticipantContext() {
        // Use default participant context (the connector itself)
        return ParticipantContext.Builder.newInstance()
                .participantContextId(config.getCatalogId())
                .apiTokenAlias("default")
                .build();
    }

    /**
     * Create an anonymous participant agent for public DSIF access.
     * DSIF read endpoints don't require authentication per spec.
     */
    private ParticipantAgent getAnonymousAgent() {
        // Anonymous agent for public catalog access
        return new ParticipantAgent(Map.of(), Map.of());
    }

    private Response problemResponse(ProblemDetail problem, Response.Status status) {
        return Response.status(status)
            .type(CONTENT_TYPE_PROBLEM_JSON)
            .header(DSIF_VERSION_HEADER, config.getDsifVersion())
            .entity(problem)
            .build();
    }
}
