# DSIF Extension Configuration Guide

This document provides detailed configuration instructions for the DSIF Independent Catalog Extension.

## Configuration Methods

The extension supports three configuration methods (in order of precedence):

1. **Java System Properties**: `-Ddsif.catalog.id=my-catalog`
2. **Environment Variables**: `DSIF_CATALOG_ID=my-catalog`
3. **Configuration Files**: `config.properties` or application configuration

## Required Configuration

These properties **must** be set for the extension to function properly:

### `dsif.catalog.id`
- **Description**: Unique identifier for this catalog within the DSIF ecosystem
- **Type**: String
- **Format**: Lowercase alphanumeric with hyphens (e.g., `afp-media-catalog`)
- **Example**: `dsif.catalog.id=afp-media-catalog`

### `dsif.catalog.sector`
- **Description**: The sector/domain this catalog belongs to
- **Type**: String
- **Common Values**: `skills`, `health`, `media`, `mobility`, `education`
- **Example**: `dsif.catalog.sector=media`

## Recommended Configuration

### `dsif.base.url`
- **Description**: Public base URL where the DSIF endpoints are accessible
- **Type**: URL
- **Default**: Auto-detected from `edc.hostname` and `web.http.port`
- **Important**: Set this explicitly for production deployments
- **Example**: `dsif.base.url=https://catalog.afp.com`

### `dsif.catalog.publisher.id`
- **Description**: DID or URI identifying the publisher organization
- **Type**: URI/DID
- **Default**: `did:web:{catalogId}`
- **Example**: `dsif.catalog.publisher.id=did:web:afp.com`

### `dsif.catalog.publisher.name`
- **Description**: Human-readable name of the publisher
- **Type**: String
- **Default**: Value of `dsif.catalog.id`
- **Example**: `dsif.catalog.publisher.name=Agence France-Presse`

## Optional Configuration

### Catalog Metadata

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dsif.catalog.title` | String | `EDC Catalog - {id}` | Display title for the catalog |
| `dsif.catalog.description` | String | `""` | Longer description of the catalog |

### Contact Information

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dsif.catalog.contact.type` | String | `owner` | Contact type (owner, support, etc.) |
| `dsif.catalog.contact.name` | String | `""` | Contact person/team name |
| `dsif.catalog.contact.email` | String | `""` | Contact email address |

### Protocol Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dsif.version` | String | `0.1.0` | DSIF specification version |
| `dsif.ttl.seconds` | Integer | `1800` | Cache TTL for manifests (30 min) |
| `dsif.auth.scheme` | String | `none` | Auth scheme: `none`, `apiKey`, `oauth2` |

### Search Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dsif.search.max.page.size` | Integer | `100` | Maximum results per page |
| `dsif.search.default.page.size` | Integer | `10` | Default results per page |

## Configuration Examples

### Minimal Configuration

```properties
dsif.catalog.id=my-catalog
dsif.catalog.sector=skills
dsif.base.url=https://my-catalog.example.org
```

### Full Production Configuration

```properties
# Catalog Identity
dsif.catalog.id=afp-media-catalog
dsif.catalog.title=AFP Media Asset Catalog
dsif.catalog.description=Comprehensive catalog of media assets from Agence France-Presse including images, videos, and multimedia content.
dsif.catalog.sector=media

# Publisher Information
dsif.catalog.publisher.id=did:web:afp.com
dsif.catalog.publisher.name=Agence France-Presse

# Contact Information
dsif.catalog.contact.type=support
dsif.catalog.contact.name=AFP Data Services Team
dsif.catalog.contact.email=dataservices@afp.com

# Network Configuration
dsif.base.url=https://catalog.afp.com

# Protocol Settings
dsif.version=0.1.0
dsif.ttl.seconds=3600
dsif.auth.scheme=none

# Search Configuration
dsif.search.max.page.size=100
dsif.search.default.page.size=20
```

### Docker Environment Variables

```yaml
environment:
  # Required
  - DSIF_CATALOG_ID=afp-media-catalog
  - DSIF_CATALOG_SECTOR=media
  - DSIF_BASE_URL=https://catalog.afp.com

  # Recommended
  - DSIF_CATALOG_PUBLISHER_ID=did:web:afp.com
  - DSIF_CATALOG_PUBLISHER_NAME=Agence France-Presse

  # Optional
  - DSIF_CATALOG_TITLE=AFP Media Asset Catalog
  - DSIF_TTL_SECONDS=3600
```

### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dsif-config
data:
  DSIF_CATALOG_ID: "afp-media-catalog"
  DSIF_CATALOG_SECTOR: "media"
  DSIF_BASE_URL: "https://catalog.afp.com"
  DSIF_CATALOG_PUBLISHER_ID: "did:web:afp.com"
  DSIF_CATALOG_PUBLISHER_NAME: "Agence France-Presse"
  DSIF_TTL_SECONDS: "3600"
```

## URL Configuration

### Base URL Detection

If `dsif.base.url` is not set, the extension attempts to auto-detect it using:

1. `edc.hostname` setting
2. `web.http.port` setting

This results in: `http://{hostname}:{port}`

### Endpoint URLs

All endpoint URLs are derived from the base URL:

| Endpoint | URL Pattern |
|----------|-------------|
| Manifest | `{base}/.well-known/dataspace-catalog.json` |
| Connection Profile | `{base}/.well-known/connection-profile` |
| Health | `{base}/dsif/health` |
| Catalog | `{base}/dsif/catalog.jsonld` |
| Dataset | `{base}/dsif/dataset/{id}.jsonld` |
| Search | `{base}/dsif/search` |

### Reverse Proxy Configuration

When running behind a reverse proxy (nginx, Kong, etc.), ensure:

1. Set `dsif.base.url` to the public-facing URL
2. Configure proxy to forward all paths correctly
3. Preserve `X-DSIF-Version` headers

Example nginx configuration:

```nginx
location /.well-known/ {
    proxy_pass http://connector:8080/.well-known/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}

location /dsif/ {
    proxy_pass http://connector:8080/dsif/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

## Validation

After configuration, verify the extension is working:

```bash
# Check health
curl -i http://localhost:8080/dsif/health

# Verify manifest
curl -i http://localhost:8080/.well-known/dataspace-catalog.json

# Check headers
curl -I http://localhost:8080/dsif/catalog.jsonld
# Should include: X-DSIF-Version: 0.1.0
```

## Troubleshooting

### Common Issues

1. **404 on endpoints**: Ensure the extension is loaded (check logs for "DSIF Catalog Extension initialized")

2. **Wrong URLs in manifest**: Set `dsif.base.url` explicitly

3. **Empty catalog**: Verify assets exist in the AssetIndex

4. **Search returns no results**: Check asset property names match expected format

### Debug Logging

Enable debug logging for the extension:

```properties
logging.level.org.dsif.edc=DEBUG
```

Or via environment variable:

```bash
export EDC_LOGGING_LEVEL_ORG_DSIF_EDC=DEBUG
```
