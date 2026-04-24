/*
 * HTTP Wire Logging Extension
 *
 * Overrides EDC's default OkHttpClient with one that has an
 * okhttp3.logging.HttpLoggingInterceptor attached as a network interceptor.
 * Used to inspect the exact payload of outbound DSP messages when debugging
 * interop with third-party connectors.
 */

plugins {
    `java-library`
}

// Disable checkstyle for this module (aligns with dsif-catalog)
tasks.withType<Checkstyle>().configureEach {
    enabled = false
}

dependencies {
    api(libs.edc.spi.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}
