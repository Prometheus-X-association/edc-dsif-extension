/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.HealthResponse;
import org.dsif.edc.extension.catalog.service.DsifHealthService;

/**
 * Controller for DSIF health endpoint.
 */
@Path("/dsif")
public class DsifHealthController {

    public static final String DSIF_VERSION_HEADER = "X-DSIF-Version";

    private final DsifHealthService healthService;
    private final DsifConfiguration config;

    public DsifHealthController(DsifHealthService healthService, DsifConfiguration config) {
        this.healthService = healthService;
        this.config = config;
    }

    /**
     * GET /dsif/health
     *
     * Returns health status for this catalog.
     * Always returns 200 OK with status field indicating actual health.
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHealth() {
        HealthResponse health = healthService.getHealth();

        return Response.ok(health)
            .header(DSIF_VERSION_HEADER, config.getDsifVersion())
            .build();
    }
}
