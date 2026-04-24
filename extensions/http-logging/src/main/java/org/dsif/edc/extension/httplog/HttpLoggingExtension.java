/*
 * DSIF HTTP Wire Logging Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.httplog;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

/**
 * Overrides EDC's default {@link OkHttpClient} with one that has an
 * {@link HttpLoggingInterceptor} attached as a network interceptor.
 *
 * <p>Purpose: debug interop failures with third-party DSP connectors by
 * logging the exact wire-level payload of every outbound HTTP request the
 * connector makes (contract negotiation, transfer request, catalog request,
 * etc.). Because EDC's {@code EdcHttpClient} is built from an {@code @Inject}ed
 * {@code OkHttpClient}, overwriting the {@code OkHttpClient} service is
 * enough to intercept all DSP traffic.
 *
 * <p>Behavior is controlled by config {@value #LEVEL_SETTING}:
 * <ul>
 *   <li>{@code NONE} (default) — interceptor installed but disabled; no output.</li>
 *   <li>{@code BASIC} — method, URL, status, duration.</li>
 *   <li>{@code HEADERS} — BASIC + request/response headers.</li>
 *   <li>{@code BODY} — HEADERS + full request/response bodies (what you want
 *       to inspect DSP JSON-LD payloads).</li>
 * </ul>
 *
 * <p>Sensitive headers ({@code Authorization}, {@code Proxy-Authorization} by
 * default, configurable via {@value #REDACT_HEADERS_SETTING}) are redacted so
 * DAT/EDR tokens do not reach the log stream. Even so, response bodies may
 * contain signed credentials; treat logs as sensitive and turn this off once
 * the bug is pinned down.
 *
 * <p>EDC DI note: this extension declares a plain {@code @Provider} for
 * {@code OkHttpClient}, same as the stock {@code RuntimeDefaultCoreServices
 * Extension}. The DI container resolves this as last-writer-wins with one
 * {@code Monitor.warning} at boot — presence of that warning is confirmation
 * the override took effect.
 */
@Extension(value = HttpLoggingExtension.NAME)
public class HttpLoggingExtension implements ServiceExtension {

    public static final String NAME = "DSIF HTTP Wire Logging";

    @Setting(description = "Logging level for the DSIF HTTP interceptor: NONE, BASIC, HEADERS, BODY",
            defaultValue = "NONE")
    public static final String LEVEL_SETTING = "dsif.http.log.level";

    @Setting(description = "Comma-separated list of HTTP headers to redact in logs",
            defaultValue = "Authorization,Proxy-Authorization")
    public static final String REDACT_HEADERS_SETTING = "dsif.http.log.redact.headers";

    @Setting(description = "OkHttp connect timeout in seconds (matches EDC default)",
            defaultValue = "30")
    public static final String CONNECT_TIMEOUT_SETTING = "edc.http.client.timeout.connect";

    @Setting(description = "OkHttp read timeout in seconds (matches EDC default)",
            defaultValue = "30")
    public static final String READ_TIMEOUT_SETTING = "edc.http.client.timeout.read";

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public OkHttpClient okHttpClient(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var config = context.getConfig();

        var level = parseLevel(config.getString(LEVEL_SETTING, "NONE"), monitor);
        var redactList = config.getString(REDACT_HEADERS_SETTING, "Authorization,Proxy-Authorization");
        var connectTimeoutSeconds = config.getInteger(CONNECT_TIMEOUT_SETTING, 30);
        var readTimeoutSeconds = config.getInteger(READ_TIMEOUT_SETTING, 30);

        var interceptor = new HttpLoggingInterceptor(msg -> monitor.info("[http-log] " + msg));
        interceptor.setLevel(level);
        Arrays.stream(redactList.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .forEach(interceptor::redactHeader);

        var client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .addNetworkInterceptor(interceptor)
                .build();

        if (level == HttpLoggingInterceptor.Level.NONE) {
            monitor.info("[http-log] OkHttpClient override loaded; logging disabled "
                    + "(set " + LEVEL_SETTING + "=BODY to enable DSP payload capture)");
        } else {
            monitor.info("[http-log] OkHttpClient override loaded; level=" + level
                    + " redact=" + redactList);
        }

        return client;
    }

    private static HttpLoggingInterceptor.Level parseLevel(String raw, Monitor monitor) {
        try {
            return HttpLoggingInterceptor.Level.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            monitor.warning("[http-log] invalid " + LEVEL_SETTING + "='" + raw
                    + "', falling back to NONE");
            return HttpLoggingInterceptor.Level.NONE;
        }
    }
}
