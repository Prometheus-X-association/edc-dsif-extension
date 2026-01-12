/*
 * DSIF Independent Catalog Extension for Eclipse Dataspace Connector
 *
 * Implements the DSIF PoC Catalogue Federation specification v0.2
 * https://gitlab.com/prometheus-x/data-space-interoperability-framework
 */

rootProject.name = "edc-dsif-extension"

include(":extensions:dsif-catalog")

// Dependency version catalog
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenCentral()
        mavenLocal()
    }
}
