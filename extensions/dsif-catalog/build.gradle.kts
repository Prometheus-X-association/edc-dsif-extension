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

// Disable checkstyle for this module (uses different code style than EDC)
tasks.withType<Checkstyle>().configureEach {
    enabled = false
}

dependencies {
    // EDC Core SPIs
    api(libs.edc.spi.core)
    api(libs.edc.spi.boot)
    api(libs.edc.spi.web)
    api(libs.edc.spi.catalog)
    api(libs.edc.spi.asset)

    // Jakarta RS for REST endpoints
    implementation(libs.jakarta.rsApi)
    implementation(libs.jakarta.json.api)

    // JSON Processing
    implementation(libs.parsson)
}
