/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reference to Connection Profile within a Federation Manifest.
 */
public class ConnectionProfileRef {

    @JsonProperty("@id")
    private String id;

    @JsonProperty("@type")
    private String type = "dsif:ConnectionProfile";

    public ConnectionProfileRef() {}

    public ConnectionProfileRef(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
}
