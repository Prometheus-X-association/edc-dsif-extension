/*
 * DSIF Independent Catalog Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog;

import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Configuration holder for DSIF extension settings.
 */
public class DsifConfiguration {

    // Configuration keys
    public static final String CATALOG_ID = "dsif.catalog.id";
    public static final String CATALOG_TITLE = "dsif.catalog.title";
    public static final String CATALOG_DESCRIPTION = "dsif.catalog.description";
    public static final String CATALOG_SECTOR = "dsif.catalog.sector";
    public static final String PUBLISHER_ID = "dsif.catalog.publisher.id";
    public static final String PUBLISHER_NAME = "dsif.catalog.publisher.name";
    public static final String CONTACT_TYPE = "dsif.catalog.contact.type";
    public static final String CONTACT_NAME = "dsif.catalog.contact.name";
    public static final String CONTACT_EMAIL = "dsif.catalog.contact.email";
    public static final String DSIF_VERSION = "dsif.version";
    public static final String TTL_SECONDS = "dsif.ttl.seconds";
    public static final String BASE_URL = "dsif.base.url";
    public static final String SEARCH_MAX_PAGE_SIZE = "dsif.search.max.page.size";
    public static final String SEARCH_DEFAULT_PAGE_SIZE = "dsif.search.default.page.size";
    public static final String AUTH_SCHEME = "dsif.auth.scheme";
    public static final String SECTOR_AUTHORITY_URL = "dsif.sector.authority.url";

    // Default values
    public static final String DEFAULT_DSIF_VERSION = "0.1.0";
    public static final int DEFAULT_TTL_SECONDS = 1800;
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final String DEFAULT_AUTH_SCHEME = "none";

    private final String catalogId;
    private final String catalogTitle;
    private final String catalogDescription;
    private final String catalogSector;
    private final String publisherId;
    private final String publisherName;
    private final String contactType;
    private final String contactName;
    private final String contactEmail;
    private final String dsifVersion;
    private final int ttlSeconds;
    private final String baseUrl;
    private final int searchMaxPageSize;
    private final int searchDefaultPageSize;
    private final String authScheme;
    private final String sectorAuthorityUrl;

    public DsifConfiguration(ServiceExtensionContext context) {
        this.catalogId = context.getSetting(CATALOG_ID, context.getComponentId());
        this.catalogTitle = context.getSetting(CATALOG_TITLE, "EDC Catalog - " + catalogId);
        this.catalogDescription = context.getSetting(CATALOG_DESCRIPTION, "");
        this.catalogSector = context.getSetting(CATALOG_SECTOR, "general");
        this.publisherId = context.getSetting(PUBLISHER_ID, "did:web:" + catalogId);
        this.publisherName = context.getSetting(PUBLISHER_NAME, catalogId);
        this.contactType = context.getSetting(CONTACT_TYPE, "owner");
        this.contactName = context.getSetting(CONTACT_NAME, "");
        this.contactEmail = context.getSetting(CONTACT_EMAIL, "");
        this.dsifVersion = context.getSetting(DSIF_VERSION, DEFAULT_DSIF_VERSION);
        this.ttlSeconds = context.getSetting(TTL_SECONDS, DEFAULT_TTL_SECONDS);
        this.baseUrl = context.getSetting(BASE_URL, detectBaseUrl(context));
        this.searchMaxPageSize = context.getSetting(SEARCH_MAX_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
        this.searchDefaultPageSize = context.getSetting(SEARCH_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE);
        this.authScheme = context.getSetting(AUTH_SCHEME, DEFAULT_AUTH_SCHEME);
        this.sectorAuthorityUrl = context.getSetting(SECTOR_AUTHORITY_URL, "");
    }

    private String detectBaseUrl(ServiceExtensionContext context) {
        // Try to auto-detect from common EDC settings
        String hostname = context.getSetting("edc.hostname", "localhost");
        int port = context.getSetting("web.http.port", 8080);
        return String.format("http://%s:%d", hostname, port);
    }

    // Endpoint URL builders
    public String getManifestUrl() {
        return baseUrl + "/.well-known/dataspace-catalog.json";
    }

    public String getConnectionProfileUrl() {
        return baseUrl + "/.well-known/connection-profile";
    }

    public String getCatalogUrl() {
        return baseUrl + "/dsif/catalog.jsonld";
    }

    public String getDatasetUrlTemplate() {
        return baseUrl + "/dsif/dataset/{id}.jsonld";
    }

    public String getSearchUrl() {
        return baseUrl + "/dsif/search";
    }

    public String getHealthUrl() {
        return baseUrl + "/dsif/health";
    }

    public String getSearchTemplate() {
        return baseUrl + "/dsif/search{?q,keyword,type,format,publisher,page,pageSize,sort}";
    }

    // Getters
    public String getCatalogId() { return catalogId; }
    public String getCatalogTitle() { return catalogTitle; }
    public String getCatalogDescription() { return catalogDescription; }
    public String getCatalogSector() { return catalogSector; }
    public String getPublisherId() { return publisherId; }
    public String getPublisherName() { return publisherName; }
    public String getContactType() { return contactType; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getDsifVersion() { return dsifVersion; }
    public int getTtlSeconds() { return ttlSeconds; }
    public String getBaseUrl() { return baseUrl; }
    public int getSearchMaxPageSize() { return searchMaxPageSize; }
    public int getSearchDefaultPageSize() { return searchDefaultPageSize; }
    public String getAuthScheme() { return authScheme; }
    public String getSectorAuthorityUrl() { return sectorAuthorityUrl; }
}
