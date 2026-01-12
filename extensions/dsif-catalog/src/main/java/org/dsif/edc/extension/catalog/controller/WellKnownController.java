/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.ConnectionProfile;
import org.dsif.edc.extension.catalog.model.FederationManifest;
import org.dsif.edc.extension.catalog.service.DsifManifestService;

/**
 * Controller for .well-known DSIF endpoints.
 *
 * Provides Federation Manifest and Connection Profile for IC discovery.
 */
@Path("/.well-known")
public class WellKnownController {

    public static final String CONTENT_TYPE_JSON_LD = "application/ld+json";
    public static final String DSIF_VERSION_HEADER = "X-DSIF-Version";

    private final DsifManifestService manifestService;
    private final DsifConfiguration config;

    public WellKnownController(DsifManifestService manifestService, DsifConfiguration config) {
        this.manifestService = manifestService;
        this.config = config;
    }

    /**
     * GET /.well-known/dataspace-catalog.json
     *
     * Returns the Federation Manifest (IC Manifest) for this catalog.
     */
    @GET
    @Path("/dataspace-catalog.json")
    @Produces(CONTENT_TYPE_JSON_LD)
    public Response getManifest() {
        FederationManifest manifest = manifestService.buildManifest();

        return Response.ok(manifest)
            .header(DSIF_VERSION_HEADER, config.getDsifVersion())
            .header("Cache-Control", "public, max-age=" + config.getTtlSeconds())
            .build();
    }

    /**
     * GET /.well-known/connection-profile
     *
     * Returns the Connection Profile for SA registration.
     */
    @GET
    @Path("/connection-profile")
    @Produces(CONTENT_TYPE_JSON_LD)
    public Response getConnectionProfile() {
        ConnectionProfile profile = manifestService.buildConnectionProfile();

        return Response.ok(profile)
            .header(DSIF_VERSION_HEADER, config.getDsifVersion())
            .header("Cache-Control", "public, max-age=" + config.getTtlSeconds())
            .build();
    }
}
