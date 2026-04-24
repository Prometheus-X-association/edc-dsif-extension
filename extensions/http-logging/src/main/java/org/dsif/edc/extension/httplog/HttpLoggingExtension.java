/*
 * DSIF HTTP Wire Logging Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.httplog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.Buffer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
 * <p>Also exposes a per-host Authorization override (demo hack): set
 * {@value #AUTH_OVERRIDE_HOSTS_SETTING} + {@value #AUTH_OVERRIDE_VALUE_SETTING}
 * and the extension replaces the Authorization header emitted by EDC's IAM
 * (typically {@code iam-mock}) with a literal string, but only for outbound
 * requests whose URL host matches. Use this to spoof a non-standard token
 * shape expected by a third-party connector during interop testing; do not
 * use it to bypass real security.
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

    @Setting(description = "Comma-separated list of hostnames where the outbound Authorization "
            + "header should be replaced by " + "dsif.http.auth.override.value"
            + ". Empty = disabled. Matches the URL host exactly (no port, no path).",
            defaultValue = "")
    public static final String AUTH_OVERRIDE_HOSTS_SETTING = "dsif.http.auth.override.hosts";

    @Setting(description = "Value to set as Authorization header on outbound requests whose URL "
            + "host is in " + "dsif.http.auth.override.hosts" + ". Supports the placeholder "
            + "{{assigner}} which is resolved from the outgoing JSON body "
            + "(DSP paths: dspace:offer.odrl:assigner, odrl:assigner, assigner). If the "
            + "placeholder cannot be resolved for a given request, the original iam-mock "
            + "Authorization header is left in place and a warning is logged.",
            defaultValue = "")
    public static final String AUTH_OVERRIDE_VALUE_SETTING = "dsif.http.auth.override.value";

    private static final String ASSIGNER_PLACEHOLDER = "{{assigner}}";

    private static final String[] ASSIGNER_JSON_PATHS = {
            "/dspace:offer/odrl:assigner",
            "/odrl:assigner",
            "/assigner",
            "/edc:assigner"
    };

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Dependency edge that forces this extension to load AFTER
     * {@code RuntimeDefaultCoreServicesExtension} — the only other provider of
     * {@code RetryPolicy<Response>}. Without it the topological sort falls
     * back to SPI discovery order, which puts our {@code @Provider OkHttpClient}
     * first and lets the stock provider overwrite ours. The field itself is
     * intentionally unused.
     */
    @Inject
    @SuppressWarnings("unused")
    private RetryPolicy<Response> retryPolicy;

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
        var authOverrideHosts = config.getString(AUTH_OVERRIDE_HOSTS_SETTING, "");
        var authOverrideValue = config.getString(AUTH_OVERRIDE_VALUE_SETTING, "");

        var loggingInterceptor = new HttpLoggingInterceptor(msg -> monitor.info("[http-log] " + msg));
        loggingInterceptor.setLevel(level);
        Arrays.stream(redactList.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .forEach(loggingInterceptor::redactHeader);

        var builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds));

        var overrideHosts = Arrays.stream(authOverrideHosts.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        if (!authOverrideValue.isEmpty() && !overrideHosts.isEmpty()) {
            builder.addInterceptor(authOverrideInterceptor(overrideHosts, authOverrideValue, monitor));
            monitor.info("[http-log] Authorization override active for hosts=" + overrideHosts);
        } else if (!authOverrideValue.isEmpty() || !overrideHosts.isEmpty()) {
            monitor.warning("[http-log] Authorization override ignored: both "
                    + AUTH_OVERRIDE_HOSTS_SETTING + " and " + AUTH_OVERRIDE_VALUE_SETTING
                    + " must be set");
        }

        // Network interceptor runs AFTER the application interceptor above,
        // so log lines reflect the overridden Authorization value.
        builder.addNetworkInterceptor(loggingInterceptor);

        if (level == HttpLoggingInterceptor.Level.NONE) {
            monitor.info("[http-log] OkHttpClient override loaded; logging disabled "
                    + "(set " + LEVEL_SETTING + "=BODY to enable DSP payload capture)");
        } else {
            monitor.info("[http-log] OkHttpClient override loaded; level=" + level
                    + " redact=" + redactList);
        }

        return builder.build();
    }

    private static okhttp3.Interceptor authOverrideInterceptor(Set<String> hosts, String template, Monitor monitor) {
        var needsAssigner = template.contains(ASSIGNER_PLACEHOLDER);
        return chain -> {
            var request = chain.request();
            if (!hosts.contains(request.url().host())) {
                return chain.proceed(request);
            }
            String resolved = template;
            if (needsAssigner) {
                var assigner = extractAssigner(request);
                if (assigner == null) {
                    monitor.warning("[http-log] Authorization override skipped for "
                            + request.url().host() + " " + request.method() + " "
                            + request.url().encodedPath()
                            + ": no assigner in request body (template needs {{assigner}})");
                    return chain.proceed(request);
                }
                resolved = template.replace(ASSIGNER_PLACEHOLDER, assigner);
                monitor.debug("[http-log] Authorization override applied for "
                        + request.url().host() + " (assigner=" + assigner + ")");
            } else {
                monitor.debug("[http-log] Authorization override applied for " + request.url().host());
            }
            var overridden = request.newBuilder().header("Authorization", resolved).build();
            return chain.proceed(overridden);
        };
    }

    /**
     * Reads the outgoing request body (only if it looks like JSON) and extracts
     * the ODRL assigner from any of {@link #ASSIGNER_JSON_PATHS}. The assigner
     * can be either a bare string or an object of shape {@code {"@id":"..."}}.
     * Returns {@code null} if the body is empty, non-JSON, or doesn't carry an
     * assigner — callers must treat that as "skip override".
     */
    private static String extractAssigner(Request request) {
        var body = request.body();
        if (body == null) {
            return null;
        }
        var contentType = body.contentType();
        if (contentType == null || !"json".equalsIgnoreCase(contentType.subtype())) {
            return null;
        }
        try (var buffer = new Buffer()) {
            body.writeTo(buffer);
            if (buffer.size() == 0L) {
                return null;
            }
            var root = JSON.readTree(buffer.readUtf8());
            for (var path : ASSIGNER_JSON_PATHS) {
                var node = root.at(path);
                var value = asIdString(node);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // fall through to null — override silently skipped for this request
        }
        return null;
    }

    private static String asIdString(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            var id = node.get("@id");
            if (id != null && id.isTextual()) {
                return id.asText();
            }
        }
        return null;
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
