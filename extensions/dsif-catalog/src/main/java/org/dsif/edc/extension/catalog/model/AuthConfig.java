/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authentication configuration for DSIF endpoints.
 */
public class AuthConfig {

    @JsonProperty("@type")
    private String type = "dsif:Auth";

    @JsonProperty("dsif:scheme")
    private String scheme;

    public AuthConfig() {}

    public AuthConfig(String scheme) {
        this.scheme = scheme;
    }

    public static AuthConfig none() {
        return new AuthConfig("none");
    }

    public static AuthConfig apiKey() {
        return new AuthConfig("apiKey");
    }

    public static AuthConfig oauth2() {
        return new AuthConfig("oauth2");
    }

    public String getType() { return type; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
}
