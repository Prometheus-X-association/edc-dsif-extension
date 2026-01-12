/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Problem detail response as per RFC 7807.
 * Extended with DSIF-specific error codes.
 */
public class ProblemDetail {

    @JsonProperty("type")
    private String type = "about:blank";

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    private int status;

    @JsonProperty("detail")
    private String detail;

    @JsonProperty("instance")
    private String instance;

    @JsonProperty("dsif:errorCode")
    private String errorCode;

    public ProblemDetail() {}

    public ProblemDetail(String title, int status, String detail, String instance, String errorCode) {
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
    }

    public static ProblemDetail badRequest(String detail, String instance, String errorCode) {
        return new ProblemDetail("Bad Request", 400, detail, instance, errorCode);
    }

    public static ProblemDetail notFound(String detail, String instance, String errorCode) {
        return new ProblemDetail("Not Found", 404, detail, instance, errorCode);
    }

    public static ProblemDetail internalError(String detail, String instance, String errorCode) {
        return new ProblemDetail("Internal Server Error", 500, detail, instance, errorCode);
    }

    public static ProblemDetail invalidPageSize(String instance) {
        return badRequest("pageSize must be between 1 and 100", instance, "INVALID_PAGE_SIZE");
    }

    public static ProblemDetail invalidPage(String instance) {
        return badRequest("page must be >= 1", instance, "INVALID_PAGE");
    }

    public static ProblemDetail invalidType(String instance) {
        return badRequest("type must be 'dataset' or 'service'", instance, "INVALID_TYPE");
    }

    public static ProblemDetail invalidSort(String instance) {
        return badRequest("sort must be 'relevance', 'recent', or 'title'", instance, "INVALID_SORT");
    }

    public static ProblemDetail datasetNotFound(String id, String instance) {
        return notFound("Dataset not found: " + id, instance, "DATASET_NOT_FOUND");
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
