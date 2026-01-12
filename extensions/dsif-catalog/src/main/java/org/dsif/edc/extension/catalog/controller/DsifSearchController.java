/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dsif.edc.extension.catalog.DsifConfiguration;
import org.dsif.edc.extension.catalog.model.ProblemDetail;
import org.dsif.edc.extension.catalog.model.SearchResult;
import org.dsif.edc.extension.catalog.service.DsifSearchService;
import org.dsif.edc.extension.catalog.service.DsifSearchService.SearchParams;

import java.util.Set;

/**
 * Controller for DSIF Neutral Search endpoint.
 */
@Path("/dsif")
public class DsifSearchController {

    public static final String CONTENT_TYPE_PROBLEM_JSON = "application/problem+json";
    public static final String DSIF_VERSION_HEADER = "X-DSIF-Version";

    private static final Set<String> VALID_TYPES = Set.of("dataset", "service");
    private static final Set<String> VALID_SORTS = Set.of("relevance", "recent", "title");

    private final DsifSearchService searchService;
    private final DsifConfiguration config;

    public DsifSearchController(DsifSearchService searchService, DsifConfiguration config) {
        this.searchService = searchService;
        this.config = config;
    }

    /**
     * GET /dsif/search
     *
     * Neutral Search endpoint for federated catalog discovery.
     *
     * @param q Full-text search query
     * @param type Filter by type: "dataset" or "service"
     * @param format Filter by media type/format
     * @param publisher Filter by publisher name
     * @param keyword Filter by keyword
     * @param page Page number (1-based, default 1)
     * @param pageSize Results per page (default 10, max 100)
     * @param sort Sort order: "relevance", "recent", or "title"
     */
    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @QueryParam("q") String q,
            @QueryParam("type") String type,
            @QueryParam("format") String format,
            @QueryParam("publisher") String publisher,
            @QueryParam("keyword") String keyword,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize,
            @QueryParam("sort") @DefaultValue("relevance") String sort) {

        String requestPath = buildRequestPath(q, type, format, publisher, keyword, page, pageSize, sort);

        // Validate parameters
        if (page < 1) {
            return problemResponse(
                ProblemDetail.invalidPage(requestPath),
                Response.Status.BAD_REQUEST
            );
        }

        if (pageSize < 1 || pageSize > config.getSearchMaxPageSize()) {
            return problemResponse(
                ProblemDetail.invalidPageSize(requestPath),
                Response.Status.BAD_REQUEST
            );
        }

        if (type != null && !type.isEmpty() && !VALID_TYPES.contains(type.toLowerCase())) {
            return problemResponse(
                ProblemDetail.invalidType(requestPath),
                Response.Status.BAD_REQUEST
            );
        }

        if (sort != null && !sort.isEmpty() && !VALID_SORTS.contains(sort.toLowerCase())) {
            return problemResponse(
                ProblemDetail.invalidSort(requestPath),
                Response.Status.BAD_REQUEST
            );
        }

        try {
            SearchParams params = SearchParams.builder()
                .q(q)
                .type(type)
                .format(format)
                .publisher(publisher)
                .keyword(keyword)
                .page(page)
                .pageSize(pageSize)
                .sort(sort)
                .build();

            SearchResult result = searchService.search(params);

            return Response.ok(result)
                .header(DSIF_VERSION_HEADER, config.getDsifVersion())
                .build();

        } catch (Exception e) {
            return problemResponse(
                ProblemDetail.internalError(
                    "Search failed: " + e.getMessage(),
                    requestPath,
                    "SEARCH_ERROR"
                ),
                Response.Status.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String buildRequestPath(String q, String type, String format, String publisher,
                                    String keyword, int page, int pageSize, String sort) {
        StringBuilder path = new StringBuilder("/dsif/search?");
        if (q != null && !q.isEmpty()) path.append("q=").append(q).append("&");
        if (type != null && !type.isEmpty()) path.append("type=").append(type).append("&");
        if (format != null && !format.isEmpty()) path.append("format=").append(format).append("&");
        if (publisher != null && !publisher.isEmpty()) path.append("publisher=").append(publisher).append("&");
        if (keyword != null && !keyword.isEmpty()) path.append("keyword=").append(keyword).append("&");
        path.append("page=").append(page).append("&");
        path.append("pageSize=").append(pageSize).append("&");
        path.append("sort=").append(sort);
        return path.toString();
    }

    private Response problemResponse(ProblemDetail problem, Response.Status status) {
        return Response.status(status)
            .type(CONTENT_TYPE_PROBLEM_JSON)
            .header(DSIF_VERSION_HEADER, config.getDsifVersion())
            .entity(problem)
            .build();
    }
}
