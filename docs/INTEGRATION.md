# DSIF Extension Integration Guide

This guide explains how to integrate the DSIF extension with your EDC connector and the broader DSIF ecosystem.

## Adding the Extension to Your Connector

### Option 1: Source Dependency

Clone or copy the extension module into your project:

```kotlin
// settings.gradle.kts
include(":extensions:dsif-catalog")

// connector/build.gradle.kts
dependencies {
    implementation(project(":extensions:dsif-catalog"))
}
```

### Option 2: Maven Dependency (Once Published)

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.dsif.edc:dsif-catalog:0.1.0")
}
```

### Option 3: Fat JAR Assembly

Include in your connector's shadow JAR:

```kotlin
// build.gradle.kts
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":extensions:dsif-catalog"))
}

tasks.withType<ShadowJar> {
    mergeServiceFiles() // Important: merges META-INF/services
}
```

## Connector Assembly Example

Here's a minimal connector configuration with DSIF support:

```kotlin
// launcher/build.gradle.kts
plugins {
    `java-library`
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    // EDC Core
    implementation("org.eclipse.edc:control-plane-core:${edcVersion}")
    implementation("org.eclipse.edc:data-plane-selector-core:${edcVersion}")

    // API & Management
    implementation("org.eclipse.edc:management-api:${edcVersion}")

    // DSIF Extension
    implementation(project(":extensions:dsif-catalog"))

    // Persistence (choose one)
    implementation("org.eclipse.edc:asset-index-sql:${edcVersion}")
    // or in-memory for testing
    implementation("org.eclipse.edc:control-plane-core:${edcVersion}")
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("connector.jar")
}
```

## DSIF Federation Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Root Authority                             │
│                    (knows all sectors)                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
            ▼               ▼               ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Sector Auth   │   │ Sector Auth   │   │ Sector Auth   │
│ (Skills)      │   │ (Health)      │   │ (Media)       │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                   │                   │
   ┌────┴────┐         ┌────┴────┐         ┌────┴────┐
   │         │         │         │         │         │
   ▼         ▼         ▼         ▼         ▼         ▼
┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐
│ IC  │   │ IC  │   │ IC  │   │ IC  │   │ IC  │   │ IC  │
│ A   │   │ B   │   │ C   │   │ D   │   │ E   │   │ F   │
└─────┘   └─────┘   └─────┘   └─────┘   └─────┘   └─────┘

IC = Independent Catalog (your EDC with DSIF extension)
```

## Registering with a Sector Authority

### Step 1: Verify Your Catalog is Running

```bash
# Check health
curl http://your-catalog:8080/dsif/health
# Expected: {"status":"ok",...}

# Verify manifest
curl http://your-catalog:8080/.well-known/dataspace-catalog.json
```

### Step 2: Get Your Connection Profile

```bash
curl http://your-catalog:8080/.well-known/connection-profile > profile.json
```

### Step 3: Register with Sector Authority

```bash
curl -X POST https://sa.your-sector.org/registerCatalog \
  -H "Content-Type: application/json" \
  -d @profile.json
```

### Step 4: Verify Registration

```bash
# Check you're listed in SA discovery
curl https://sa.your-sector.org/discovery | jq '.[] | select(.catalogId == "your-catalog")'
```

## Asset Configuration for DCAT Mapping

For optimal integration with DSIF, configure your EDC assets with these properties:

### Creating DCAT-Compatible Assets

```java
Asset asset = Asset.Builder.newInstance()
    .id("unique-asset-id")
    .property("name", "Skills Dataset 2024")
    .property("description", "Comprehensive skills taxonomy dataset")
    .property("contenttype", "application/json")
    .property("keywords", "skills,education,taxonomy")
    .property("publisher", "Your Organization")
    .property("publisher:id", "did:web:your-org.com")
    .property("type", "dataset") // or "service"
    .property("modified", "2024-01-15T10:00:00Z")
    .build();
```

### For DataServices

```java
Asset dataService = Asset.Builder.newInstance()
    .id("skills-api-v1")
    .property("name", "Skills API")
    .property("description", "REST API for skills data")
    .property("type", "service")
    .property("endpointUrl", "https://api.example.org/skills/v1")
    .property("contenttype", "application/json")
    .build();
```

## Cross-Sector Federation

To enable cross-sector discovery (via Sector Authority):

1. Your IC registers with its primary Sector Authority
2. Users query SA with `scope` parameter for cross-sector search
3. SA contacts Root Authority for other sector endpoints
4. SA aggregates results from multiple sectors

Example cross-sector search:

```bash
# Search Skills SA, also including Media sector
curl "https://sa.skills.org/federate/search?q=training&scope=media&hop=1"
```

## Testing the Integration

### Unit Testing

```java
@Test
void shouldReturnValidManifest() {
    var manifest = manifestService.buildManifest();

    assertThat(manifest.getId()).isNotNull();
    assertThat(manifest.getType()).isEqualTo("dsif:ICManifest");
    assertThat(manifest.getSector()).isEqualTo("skills");
}
```

### Integration Testing

```java
@Test
void shouldSearchCatalog() {
    // Add test assets
    assetIndex.create(testAsset);

    // Search
    var result = given()
        .when()
        .get("/dsif/search?q=test")
        .then()
        .statusCode(200)
        .extract()
        .as(SearchResult.class);

    assertThat(result.getHits()).isNotEmpty();
}
```

### End-to-End Testing

```bash
#!/bin/bash
# e2e-test.sh

BASE_URL="http://localhost:8080"

echo "Testing DSIF endpoints..."

# Health
echo "Health check..."
curl -sf "$BASE_URL/dsif/health" | jq .

# Manifest
echo "Manifest..."
curl -sf "$BASE_URL/.well-known/dataspace-catalog.json" | jq .

# Catalog
echo "Catalog..."
curl -sf "$BASE_URL/dsif/catalog.jsonld" | jq .

# Search
echo "Search..."
curl -sf "$BASE_URL/dsif/search?q=test" | jq .

echo "All tests passed!"
```

## Monitoring and Observability

### Health Monitoring

Set up periodic health checks:

```yaml
# prometheus scrape config
scrape_configs:
  - job_name: 'dsif-catalog'
    metrics_path: '/dsif/health'
    static_configs:
      - targets: ['your-catalog:8080']
```

### Logging

The extension logs key events:

```
INFO  DsifCatalogExtension - Initializing DSIF Catalog Extension v0.1.0
INFO  DsifCatalogExtension - DSIF Catalog ID: my-catalog
INFO  DsifCatalogExtension - DSIF Catalog Sector: skills
INFO  DsifCatalogExtension - DSIF Base URL: https://my-catalog.example.org
```

### Metrics (Future)

Planned metrics support:

- `dsif_search_requests_total` - Total search requests
- `dsif_search_duration_seconds` - Search latency
- `dsif_catalog_assets_total` - Total assets in catalog

## Security Considerations

### Public Endpoints

The following endpoints are designed to be public (no authentication):

- `/.well-known/dataspace-catalog.json`
- `/.well-known/connection-profile`
- `/dsif/health`
- `/dsif/catalog.jsonld`
- `/dsif/search`

### Protected Operations

For production deployments, consider:

1. Rate limiting on search endpoints
2. IP allowlisting for sensitive sectors
3. mTLS for SA-to-IC communication

### Future Authentication Support

Set `dsif.auth.scheme` to advertise authentication requirements:

- `none` - No authentication required
- `apiKey` - API key in header
- `oauth2` - OAuth2 bearer token

## Troubleshooting

### Extension Not Loading

Check that:
1. META-INF/services file exists
2. Extension class is on classpath
3. No dependency conflicts

```bash
# Check services file
jar tf connector.jar | grep "META-INF/services"
unzip -p connector.jar META-INF/services/org.eclipse.edc.spi.system.ServiceExtension
```

### Empty Catalog

Verify assets exist:

```bash
curl http://localhost:8080/management/v3/assets/request \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: your-api-key" \
  -d '{}'
```

### Search Not Working

Check that assets have searchable properties:
- `name` - Required for title
- `description` - Used in full-text search
- `keywords` - Used in keyword filter
