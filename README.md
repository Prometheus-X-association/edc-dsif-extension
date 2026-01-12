# DSIF Independent Catalog Extension for EDC

This extension implements the **DSIF (Data Space Interoperability Framework) Independent Catalog (IC)** specification for Eclipse Dataspace Connector (EDC).

It enables an EDC connector to participate in the DSIF federated catalogue ecosystem by exposing standardized endpoints for discovery, search, and health monitoring.

## Specification

This extension implements the [DSIF PoC Catalogue Federation v0.2](https://gitlab.com/prometheus-x/data-space-interoperability-framework/-/wikis/PoC-Catalogue-Federation) specification.

## Architecture

**Key insight**: This extension leverages EDC's existing DCAT-compliant catalog infrastructure rather than reimplementing models from scratch.

### What EDC Provides (Reused)

- **Catalog SPI**: `Catalog`, `Dataset`, `Distribution`, `DataService` models
- **DatasetResolver**: Asset → Dataset transformation
- **AssetIndex** with **QuerySpec**: Filtering, pagination, and sorting

### What This Extension Adds (DSIF-specific)

- **Hydra pagination format** for DCAT catalogs
- **DSIF provenance fields**: `originCatalogId`, `federationPath`, `hopCount`
- **Federation Manifest** and **Connection Profile** for discovery
- **Neutral Search** response format

This design keeps the extension thin while providing full DSIF compliance.

## Features

- **Federation Manifest** at `/.well-known/dataspace-catalog.json`
- **Connection Profile** at `/.well-known/connection-profile`
- **Health endpoint** at `/dsif/health`
- **DCAT Catalog** at `/dsif/catalog.jsonld` with Hydra pagination
- **Individual datasets** at `/dsif/dataset/{id}.jsonld`
- **Neutral Search** at `/dsif/search`

## Quick Start

### Option 1: Use with Local Connector (Recommended)

This is the recommended approach to use the latest EDC APIs.

**Step 1:** Copy or symlink the dsif-catalog module into your Connector project:

```bash
# From your Connector directory
cp -r /path/to/edc-dsif-extension/extensions/dsif-catalog extensions/dsif-catalog
```

**Step 2:** Add to Connector's settings.gradle.kts:

```kotlin
include(":extensions:dsif-catalog")
```

**Step 3:** Add dependency in your launcher's build.gradle.kts:

```kotlin
dependencies {
    implementation(project(":extensions:dsif-catalog"))
}
```

**Step 4:** Build the Connector:

```bash
./gradlew :extensions:dsif-catalog:build
./gradlew :launchers:your-launcher:shadowJar
```

### Option 2: Standalone Build with Published EDC

For use with published EDC releases (currently 0.14.0):

```bash
cd edc-dsif-extension
./gradlew build
```

Note: Some newer EDC APIs (like ParticipantContext) may not be available in published releases.

### Configuration

Set these required properties in your connector configuration:

```properties
# Required
dsif.catalog.id=your-catalog-id
dsif.catalog.sector=media

# Optional (auto-detected if not set)
dsif.base.url=http://localhost:8080
```

- **catalog.id**: Unique identifier for your catalog (e.g., `afp-media-catalog`, `tems-participant-a`)
- **sector**: The domain your data belongs to (e.g., `media`, `skills`, `health`, `mobility`)
- **base.url**: Your connector's public URL. Use `http://localhost:8080` for local development.

### Start Your Connector

The DSIF endpoints will be available automatically:
- http://localhost:8080/.well-known/dataspace-catalog.json
- http://localhost:8080/dsif/health
- http://localhost:8080/dsif/catalog.jsonld
- http://localhost:8080/dsif/search

## Configuration

### Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `dsif.catalog.id` | Unique catalog identifier | `my-catalog` |
| `dsif.catalog.sector` | Sector name | `skills`, `health`, `media` |

### Optional Properties

| Property | Description | Default |
|----------|-------------|---------|
| `dsif.catalog.title` | Human-readable catalog title | `EDC Catalog - {id}` |
| `dsif.catalog.description` | Catalog description | `""` |
| `dsif.catalog.publisher.id` | Publisher DID or URI | `did:web:{catalogId}` |
| `dsif.catalog.publisher.name` | Publisher name | `{catalogId}` |
| `dsif.catalog.contact.type` | Contact type | `owner` |
| `dsif.catalog.contact.name` | Contact name | `""` |
| `dsif.catalog.contact.email` | Contact email | `""` |
| `dsif.version` | DSIF spec version | `0.1.0` |
| `dsif.ttl.seconds` | Cache TTL for manifests | `1800` |
| `dsif.base.url` | Public base URL | Auto-detected |
| `dsif.search.max.page.size` | Max search page size | `100` |
| `dsif.search.default.page.size` | Default page size | `10` |
| `dsif.auth.scheme` | Auth scheme (`none`, `apiKey`, `oauth2`) | `none` |

### Example Configuration

```properties
# DSIF Extension Configuration
dsif.catalog.id=afp-media-catalog
dsif.catalog.title=AFP Media Catalog
dsif.catalog.description=Media assets from AFP
dsif.catalog.sector=media

dsif.catalog.publisher.id=did:web:afp.com
dsif.catalog.publisher.name=Agence France-Presse

dsif.catalog.contact.type=support
dsif.catalog.contact.name=AFP Support
dsif.catalog.contact.email=support@afp.com

dsif.base.url=https://catalog.afp.com
dsif.ttl.seconds=3600
```

### Environment Variables

All properties can be set via environment variables using the EDC convention:

```bash
export DSIF_CATALOG_ID=my-catalog
export DSIF_CATALOG_SECTOR=skills
export DSIF_BASE_URL=https://my-catalog.example.org
```

## API Endpoints

### Federation Manifest

```bash
GET /.well-known/dataspace-catalog.json
```

Returns the IC Manifest in JSON-LD format:

```json
{
  "@context": [...],
  "@id": "https://example.org/.well-known/dataspace-catalog.json",
  "@type": "dsif:ICManifest",
  "dct:title": "My Catalog - Manifest",
  "dsif:sector": "skills",
  "dsif:ttlSeconds": 1800,
  "dsif:version": "0.1.0"
}
```

### Health Check

```bash
GET /dsif/health
```

Returns health status:

```json
{
  "status": "ok",
  "version": "0.1.0",
  "uptime": 3600,
  "dsifVersion": "0.1.0",
  "catalogId": "my-catalog"
}
```

### DCAT Catalog

```bash
GET /dsif/catalog.jsonld?page=1
```

Returns DCAT catalog with Hydra pagination:

```json
{
  "@context": [...],
  "@id": "https://example.org/dsif/catalog.jsonld?page=1",
  "@type": "dcat:Catalog",
  "dcat:dataset": [...],
  "hydra:view": {
    "@type": "hydra:PartialCollectionView",
    "hydra:first": "...?page=1",
    "hydra:next": "...?page=2",
    "hydra:totalItems": 50
  }
}
```

### Individual Dataset

```bash
GET /dsif/dataset/{id}.jsonld
```

Returns individual dataset in DCAT JSON-LD format.

### Neutral Search

```bash
GET /dsif/search?q=skills&type=dataset&page=1&pageSize=10&sort=relevance
```

Query parameters:
- `q` - Full-text search
- `type` - `dataset` or `service`
- `format` - Filter by media type
- `publisher` - Filter by publisher
- `keyword` - Filter by keyword
- `page` - Page number (default: 1)
- `pageSize` - Results per page (default: 10, max: 100)
- `sort` - `relevance`, `recent`, or `title`

Returns:

```json
{
  "total": 42,
  "page": 1,
  "pageSize": 10,
  "hits": [
    {
      "uri": "urn:dsif:my-catalog:asset-123",
      "type": "dataset",
      "title": "Skills Dataset",
      "publisher": "Example Org",
      "summary": "...",
      "keywords": ["skills", "education"],
      "formats": ["application/json"],
      "provenance": {
        "originCatalogId": "my-catalog",
        "federationPath": [],
        "hopCount": 0
      }
    }
  ]
}
```

## Asset Mapping

EDC Assets are automatically mapped to DCAT Datasets/DataServices. For optimal results, include these properties on your assets:

| EDC Property | DCAT Mapping | Required |
|--------------|--------------|----------|
| `name` | `dct:title` | Yes |
| `description` | `dct:description` | No |
| `contenttype` | `dcat:mediaType` | Recommended |
| `keywords` | `dcat:keyword` | No |
| `publisher` | `dct:publisher` | Recommended |
| `type` | `@type` (`dataset`/`service`) | No |
| `endpointUrl` | `dcat:endpointURL` | For services |

## Registering with a Sector Authority

To participate in DSIF federation, register your catalog with a Sector Authority:

```bash
curl -X POST https://sa.example.org/registerCatalog \
  -H "Content-Type: application/json" \
  -d @- << EOF
{
  "catalogId": "my-catalog",
  "type": "ic",
  "sector": "skills",
  "endpoints": {
    "catalog": "https://my-catalog.example.org/dsif/catalog.jsonld",
    "search": "https://my-catalog.example.org/dsif/search",
    "health": "https://my-catalog.example.org/dsif/health"
  },
  "conformsTo": ["https://www.w3.org/TR/vocab-dcat-3/"],
  "capabilities": {
    "search": {
      "q": true,
      "filters": ["format", "publisher", "keyword"],
      "paging": true
    }
  },
  "lastUpdated": "2025-01-12T10:00:00Z",
  "ttlSeconds": 1800
}
EOF
```

## Docker Deployment

Example Docker Compose configuration:

```yaml
services:
  connector:
    image: my-edc-connector:latest
    environment:
      - DSIF_CATALOG_ID=my-catalog
      - DSIF_CATALOG_TITLE=My Catalog
      - DSIF_CATALOG_SECTOR=skills
      - DSIF_BASE_URL=https://my-catalog.example.org
      - EDC_HOSTNAME=my-catalog.example.org
    ports:
      - "8080:8080"
```

## Testing

Run unit tests:

```bash
./gradlew test
```

Test endpoints manually:

```bash
# Health check
curl http://localhost:8080/dsif/health

# Manifest
curl http://localhost:8080/.well-known/dataspace-catalog.json

# Catalog
curl http://localhost:8080/dsif/catalog.jsonld

# Search
curl "http://localhost:8080/dsif/search?q=test"
```

## License

Apache License 2.0

## Contributing

Contributions are welcome! Please read the contributing guidelines before submitting PRs.

## References

- [DSIF Specification](https://gitlab.com/prometheus-x/data-space-interoperability-framework/-/wikis/PoC-Catalogue-Federation)
- [W3C DCAT 3](https://www.w3.org/TR/vocab-dcat-3/)
- [Eclipse Dataspace Components](https://eclipse-edc.github.io)
- [Prometheus-X](https://prometheus-x.org)
