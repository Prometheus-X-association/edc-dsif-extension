/*
 * DSP Validation Logging Extension
 *
 * Registers a JAX-RS filter on the EDC "protocol" web context that logs the
 * full request body for every inbound DSP request and the response body for
 * any non-2xx response. EDC's contract validation returns Result.failure
 * which gets serialised to a 400 ContractNegotiationError with the reason
 * in the response body — but the validator itself does not log via Monitor.
 * This filter surfaces those reasons (and the matching inbound payload) so
 * you can diff them against what the management API has stored.
 */

plugins {
    `java-library`
}

tasks.withType<Checkstyle>().configureEach {
    enabled = false
}

dependencies {
    api(libs.edc.spi.core)
    api(libs.edc.spi.web)
    implementation(libs.jakarta.rsApi)
}
