# DSIF Independent Catalog Extension for EDC

## Overview

This document outlines the implementation plan for an Eclipse Dataspace Connector (EDC) extension that implements the **DSIF (Data Space Interoperability Framework) Independent Catalog (IC)** specification.

The extension enables an EDC connector to participate in the DSIF federated catalogue ecosystem by exposing standardized endpoints for discovery, search, and health monitoring.

## Architecture Decision: Leverage EDC Infrastructure

**Key insight**: EDC already provides DCAT-compliant catalog infrastructure. This extension acts as a thin wrapper that:

1. **Uses existing EDC models**: `Catalog`, `Dataset`, `Distribution`, `DataService`
2. **Uses existing services**:
   - `DatasetResolver` for Asset → Dataset transformation (when participant context available)
   - `AssetIndex` with `QuerySpec` for filtering, pagination, sorting (fallback for anonymous access)
3. **Adds DSIF-specific features**:
   - Hydra pagination format
   - DSIF provenance fields (`originCatalogId`, `federationPath`, `hopCount`)
   - Federation Manifest and Connection Profile
   - Neutral Search response format

## EDC Version Compatibility

- **Target EDC Version**: 0.15.1
- **Tested with**: edc-connector (STBX EDC Connector)

## Reference Specifications

- **DSIF PoC Catalogue Federation v0.2**: [Prometheus-X DSIF Wiki](https://gitlab.com/prometheus-x/data-space-interoperability-framework/-/wikis/PoC-Catalogue-Federation)
- **W3C DCAT v3**: https://www.w3.org/TR/vocab-dcat-3/
- **CKAN DCAT Extension**: https://extensions.ckan.org/extension/dcat/
- **RFC 7807**: Problem Details for HTTP APIs

## Endpoints Implemented

| Endpoint | Method | Description | Content-Type |
|----------|--------|-------------|--------------|
| `/.well-known/dataspace-catalog.json` | GET | Federation Manifest (IC self-description) | application/ld+json |
| `/.well-known/connection-profile` | GET | Connection Profile for SA registration | application/ld+json |
| `/dsif/health` | GET | Health status endpoint | application/json |
| `/dsif/catalog.jsonld` | GET | DCAT catalog with Hydra pagination | application/ld+json |
| `/dsif/dataset/{id}.jsonld` | GET | Individual dataset DCAT JSON-LD | application/ld+json |
| `/dsif/search` | GET | Neutral Search endpoint | application/json |

## Implementation Architecture

### What EDC Provides (Reused)

```
EDC Core
├── Catalog SPI
│   ├── Catalog.java         → DCAT Catalog model
│   ├── Dataset.java         → DCAT Dataset model
│   ├── Distribution.java    → DCAT Distribution model
│   ├── DataService.java     → DCAT DataService model
│   └── DatasetResolver.java → Asset → Dataset transformation
├── Core SPI
│   ├── QuerySpec.java       → Pagination, filtering, sorting
│   └── Criterion.java       → Filter criteria
└── Asset SPI
    └── AssetIndex.java      → Query assets with QuerySpec
```

### What We Add (DSIF-specific)

```
DSIF Extension
├── model/                    → DSIF-specific DTOs only
│   ├── FederationManifest    → IC Manifest (DSIF-specific)
│   ├── ConnectionProfile     → SA registration (DSIF-specific)
│   ├── SearchResult          → Neutral Search format (different from DCAT)
│   ├── SearchHit             → Individual search hit
│   ├── Provenance            → Federation tracking
│   ├── HealthResponse        → DSIF health format
│   └── ProblemDetail         → RFC 7807 errors
├── service/
│   ├── DsifManifestService   → Builds DSIF manifests
│   ├── DsifCatalogService    → Wraps DatasetResolver + adds Hydra
│   ├── DsifSearchService     → Wraps AssetIndex + transforms to SearchResult
│   └── DsifHealthService     → Health monitoring
└── controller/
    ├── WellKnownController   → /.well-known/* endpoints
    ├── DsifCatalogController → /dsif/catalog.jsonld, /dsif/dataset/*
    ├── DsifSearchController  → /dsif/search
    └── DsifHealthController  → /dsif/health
```

## Models Breakdown

### Reused from EDC (no custom DTOs needed)

| EDC Model | Usage |
|-----------|-------|
| `Catalog` | Catalog response structure |
| `Dataset` | Dataset/DataService representation |
| `Distribution` | Access methods for datasets |
| `DataService` | Endpoint definitions |
| `QuerySpec` | Filtering, pagination, sorting |
| `Criterion` | Individual filter conditions |
| `Asset` | Source data from AssetIndex |

### DSIF-Specific Models (must implement)

| Model | Purpose |
|-------|---------|
| `FederationManifest` | IC self-description for discovery |
| `ConnectionProfile` | Registration with Sector Authority |
| `SearchResult` | Neutral Search response format |
| `SearchHit` | Individual search result item |
| `Provenance` | Federation path tracking |
| `HealthResponse` | DSIF health check format |
| `ProblemDetail` | RFC 7807 error responses |

## Service Layer

### DsifCatalogService

```java
// Uses EDC's DatasetResolver with AssetIndex fallback for anonymous access
public JsonObject getCatalog(int page, ParticipantContext ctx,
                              ParticipantAgent agent, String protocol) {
    // 1. Build QuerySpec with pagination
    QuerySpec querySpec = QuerySpec.Builder.newInstance()
        .offset((page - 1) * pageSize)
        .limit(pageSize)
        .build();

    List<Dataset> datasets;
    long totalItems;

    // 2. Try DatasetResolver if participant context available
    if (ctx != null && agent != null) {
        datasets = datasetResolver.query(ctx, agent, querySpec, protocol)
            .collect(toList());
        totalItems = datasetResolver.query(ctx, agent, QuerySpec.max(), protocol)
            .count();
    } else {
        // 3. Fallback to AssetIndex for anonymous/public access
        datasets = assetIndex.queryAssets(querySpec)
            .map(this::assetToDataset)
            .collect(toList());
        totalItems = assetIndex.queryAssets(QuerySpec.max()).count();
    }

    // 4. Build JSON-LD with Hydra pagination + DSIF provenance
    return buildCatalogJsonLd(datasets, page, totalItems);
}
```

**Note**: The AssetIndex fallback enables anonymous catalog browsing via DSIF endpoints without requiring DSP protocol authentication. This is essential for public catalog discovery.

### DsifSearchService

```java
// Uses EDC's AssetIndex with QuerySpec
public SearchResult search(SearchParams params) {
    // 1. Build QuerySpec with filters using EDC's Criterion
    QuerySpec.Builder builder = QuerySpec.Builder.newInstance()
        .offset((params.page - 1) * pageSize)
        .limit(pageSize);

    if (params.format != null) {
        builder.filter(new Criterion("contenttype", "=", params.format));
    }

    // 2. Query AssetIndex (reuses EDC's filtering)
    List<Asset> assets = assetIndex.queryAssets(builder.build())
        .filter(this::matchesFullText)  // Post-filter for full-text
        .collect(toList());

    // 3. Transform to DSIF SearchResult format
    return buildSearchResult(assets, params);
}
```

## Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `dsif.catalog.id` | Unique catalog identifier | (required) |
| `dsif.catalog.sector` | Sector name (e.g., "skills", "media") | (required) |
| `dsif.catalog.title` | Human-readable catalog title | `EDC Catalog - {id}` |
| `dsif.catalog.description` | Catalog description | `""` |
| `dsif.catalog.publisher.id` | Publisher DID or URI | `did:web:{catalogId}` |
| `dsif.catalog.publisher.name` | Publisher name | `{catalogId}` |
| `dsif.base.url` | Public base URL for endpoints | (auto-detect) |
| `dsif.ttl.seconds` | Cache TTL for manifests | `1800` |
| `dsif.search.max.page.size` | Maximum page size for search | `100` |
| `dsif.search.default.page.size` | Default page size | `10` |

## Implementation Status

### Completed

- [x] Project structure and build configuration
- [x] Configuration management (`DsifConfiguration`)
- [x] DSIF-specific models (FederationManifest, SearchResult, etc.)
- [x] Manifest service (`DsifManifestService`)
- [x] Health service (`DsifHealthService`)
- [x] Catalog service using `DatasetResolver` with `AssetIndex` fallback (`DsifCatalogService`)
- [x] Search service using `AssetIndex` + `QuerySpec` (`DsifSearchService`)
- [x] REST controllers for all endpoints
- [x] ServiceExtension with proper dependency injection
- [x] Integration with edc-connector via symlink
- [x] EDC 0.15.1 version alignment
- [x] Maven coordinates for dependencies (instead of project references)
- [x] Anonymous catalog browsing (AssetIndex fallback when no participant context)

### Remaining

- [ ] Unit tests
- [ ] Integration tests
- [ ] SHACL validation for DCAT output
- [ ] JSON Schema validation for manifests
- [ ] Improve property mapping in catalog (use `name` property for title)

## Integration with edc-connector

The extension is integrated into the STBX EDC Connector via symlink:

```
edc-connector/extensions/dsif-catalog -> ../../edc-dsif-extension/extensions/dsif-catalog
```

### Build Configuration

The `build.gradle.kts` uses Maven coordinates (not project references) for EDC dependencies:

```kotlin
dependencies {
    // EDC Core SPIs (using Maven coordinates via version catalog)
    api(libs.edc.spi.core)
    api(libs.edc.spi.boot)
    api(libs.edc.spi.web)
    api(libs.edc.spi.catalog)
    api(libs.edc.spi.asset)

    // Jakarta RS for REST endpoints
    implementation(libs.jakarta.rs.api)
    implementation(libs.jakarta.json.api)
    implementation(libs.parsson)
}
```

### Notes

- Checkstyle is disabled for this module (different code style than EDC)
- `@Setting` annotations removed to avoid EDC annotation processor issues
- Configuration is read via `ServiceExtensionContext.getSetting()` in `DsifConfiguration`

## Acceptance Criteria

From a clean deployment with this extension, a client can:

1. `GET /.well-known/dataspace-catalog.json` → Valid IC Manifest
2. `GET /.well-known/connection-profile` → Valid Connection Profile
3. `GET /dsif/health` → `{"status": "ok", ...}`
4. `GET /dsif/catalog.jsonld` → DCAT JSON-LD with Hydra pagination
5. `GET /dsif/dataset/{id}.jsonld` → Individual dataset DCAT
6. `GET /dsif/search?q=test` → SearchResult with hits
7. All responses include `X-DSIF-Version: 0.1.0` header
8. Invalid requests return RFC 7807 problem+json responses

## References

- [DSIF PoC Catalogue Federation](https://gitlab.com/prometheus-x/data-space-interoperability-framework/-/wikis/PoC-Catalogue-Federation)
- [W3C DCAT 3](https://www.w3.org/TR/vocab-dcat-3/)
- [EDC Extension Development](https://eclipse-edc.github.io)
- [EDC Catalog SPI](https://github.com/eclipse-edc/Connector/tree/main/spi/control-plane/catalog-spi)
