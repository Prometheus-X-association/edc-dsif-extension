/*
 * DSIF Independent Catalog Extension Module
 *
 * Provides endpoints for DSIF catalogue federation:
 * - /.well-known/dataspace-catalog.json (Federation Manifest)
 * - /dsif/health (Health check)
 * - /dsif/catalog.jsonld (DCAT catalog with Hydra pagination)
 * - /dsif/dataset/{id}.jsonld (Individual dataset)
 * - /dsif/search (Neutral Search)
 */

plugins {
    `java-library`
}

dependencies {
    // EDC Core SPIs
    api(project(":spi:common:core-spi"))
    api(project(":spi:common:boot-spi"))
    api(project(":spi:common:web-spi"))
    api(project(":spi:common:participant-spi"))
    api(project(":spi:common:connector-participant-context-spi"))

    // EDC Control Plane SPIs
    api(project(":spi:control-plane:catalog-spi"))
    api(project(":spi:control-plane:asset-spi"))

    // Jakarta RS for REST endpoints (use Connector's version catalog)
    implementation(libs.jakarta.rsApi)
    implementation(libs.jakarta.json.api)

    // JSON Processing
    implementation(libs.parsson)
}
