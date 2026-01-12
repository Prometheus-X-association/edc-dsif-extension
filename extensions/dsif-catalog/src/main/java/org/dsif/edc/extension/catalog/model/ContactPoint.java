/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contact point information using schema.org vocabulary.
 */
public class ContactPoint {

    @JsonProperty("@type")
    private String type = "schema:ContactPoint";

    @JsonProperty("schema:contactType")
    private String contactType;

    @JsonProperty("schema:name")
    private String name;

    @JsonProperty("schema:email")
    private String email;

    public ContactPoint() {}

    public ContactPoint(String contactType, String name, String email) {
        this.contactType = contactType;
        this.name = name;
        this.email = email;
    }

    public String getType() { return type; }
    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
