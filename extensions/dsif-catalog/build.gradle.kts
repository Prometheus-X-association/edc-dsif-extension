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
    `maven-publish`
}

val edcGroup: String by project
val edcVersion: String by project
val titaniumVersion: String by project
val jakartaRsVersion: String by project
val jakartaJsonVersion: String by project
val jakartaValidationVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project
val assertjVersion: String by project

// When building as part of Connector project, use project() dependencies
// When building standalone with published EDC, use group:artifact:version dependencies

val useLocalConnector = project.findProperty("useLocalConnector")?.toString()?.toBoolean() ?: false

dependencies {
    if (useLocalConnector) {
        // Use project dependencies from local Connector
        api(project(":spi:common:core-spi"))
        api(project(":spi:common:boot-spi"))
        api(project(":spi:common:web-spi"))
        api(project(":spi:common:participant-spi"))
        api(project(":spi:common:connector-participant-context-spi"))
        api(project(":spi:control-plane:catalog-spi"))
        api(project(":spi:control-plane:asset-spi"))
        testImplementation(project(":core:control-plane:control-plane-core"))
    } else {
        // Use published Maven artifacts
        api("${edcGroup}:runtime-metamodel:${edcVersion}")
        api("${edcGroup}:core-spi:${edcVersion}")
        api("${edcGroup}:boot-spi:${edcVersion}")
        api("${edcGroup}:web-spi:${edcVersion}")
        api("${edcGroup}:catalog-spi:${edcVersion}")
        api("${edcGroup}:asset-spi:${edcVersion}")
        api("${edcGroup}:participant-spi:${edcVersion}")
        // Note: participant-context-spi might need local build for newer EDC versions
    }

    // Jakarta RS for REST endpoints
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:${jakartaRsVersion}")
    implementation("jakarta.json:jakarta.json-api:${jakartaJsonVersion}")
    implementation("jakarta.validation:jakarta.validation-api:${jakartaValidationVersion}")

    // JSON-LD Processing
    implementation("com.apicatalog:titanium-json-ld:${titaniumVersion}")
    implementation("org.eclipse.parsson:parsson:1.1.5")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:${junitVersion}")
    testImplementation("org.mockito:mockito-core:${mockitoVersion}")
    testImplementation("org.mockito:mockito-junit-jupiter:${mockitoVersion}")
    testImplementation("org.assertj:assertj-core:${assertjVersion}")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = project.name
        }
    }
}
