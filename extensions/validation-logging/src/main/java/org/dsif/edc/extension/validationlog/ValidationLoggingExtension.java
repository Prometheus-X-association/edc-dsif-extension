/*
 * DSP Validation Logging Extension
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.validationlog;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Surfaces inbound DSP request bodies, request headers, and 4xx/5xx response
 * bodies + headers in the controlplane log.
 *
 * <p>Why this exists: when an inbound DSP message (e.g.
 * {@code POST /protocol/2025-1/negotiations/{id}/agreement}) fails validation
 * inside {@code ContractValidationServiceImpl}, EDC returns a
 * {@code Result.failure(reason)} which the protocol layer serialises to a
 * 400 {@code ContractNegotiationError}. The reason ends up in the HTTP
 * response body, but the validator never calls {@code Monitor.warn/error},
 * so nothing about the failure appears in {@code docker compose logs} —
 * only the silent SEVERE for unexpected exceptions makes it through.
 *
 * <p>Arrow convention (matches {@code [http-log]} from the OkHttp side):
 * <ul>
 *   <li>{@code <-- } means traffic <em>arriving</em> at us — for this filter,
 *       inbound DSP requests from peers.</li>
 *   <li>{@code --> } means traffic <em>leaving</em> us — for this filter, the
 *       responses we return (mostly 4xx during interop debugging).</li>
 * </ul>
 *
 * <p>So per direction:
 * <ul>
 *   <li>{@code [http-log] -->} = our outgoing DSP request</li>
 *   <li>{@code [http-log] <--} = peer's response coming back to us</li>
 *   <li>{@code [dsp-debug] <--} = peer's incoming DSP request hitting us</li>
 *   <li>{@code [dsp-debug] -->} = our outgoing response back to the peer</li>
 * </ul>
 *
 * <p>Configurable via:
 * <ul>
 *   <li>{@value #LOG_2XX_SETTING} — set to {@code true} to also log 2xx
 *       response bodies (heavyweight; off by default).</li>
 *   <li>{@value #MAX_BODY_BYTES_SETTING} — truncate logged bodies above this
 *       size. Default 16 KB.</li>
 *   <li>{@value #REDACT_HEADERS_SETTING} — comma-separated header names to
 *       replace with {@code <redacted>} in the log. Defaults to
 *       {@code Authorization,Proxy-Authorization,Cookie,Set-Cookie}.</li>
 * </ul>
 *
 * <p>Heads up: bodies on DSP endpoints can include serialised tokens. The
 * filter only redacts <em>headers</em> by default, not body fields. Treat
 * the controlplane log as sensitive while this is enabled, and turn it off
 * once the bug is pinned down.
 */
@Extension(value = ValidationLoggingExtension.NAME)
public class ValidationLoggingExtension implements ServiceExtension {

    public static final String NAME = "DSP Validation Logging";

    @Setting(description = "Also log 2xx response bodies (default off — only 4xx/5xx)",
            defaultValue = "false")
    public static final String LOG_2XX_SETTING = "dsif.validation.log.2xx";

    @Setting(description = "Max bytes of body to log per request/response. Truncated above this.",
            defaultValue = "16384")
    public static final String MAX_BODY_BYTES_SETTING = "dsif.validation.log.maxBodyBytes";

    @Setting(description = "Comma-separated header names to redact in inbound/outbound logging "
            + "(case-insensitive).",
            defaultValue = "Authorization,Proxy-Authorization,Cookie,Set-Cookie")
    public static final String REDACT_HEADERS_SETTING = "dsif.validation.log.redact.headers";

    private static final String CONTEXT = "protocol";

    @Inject
    private WebService webService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext ctx) {
        var monitor = ctx.getMonitor();
        var log2xx = ctx.getConfig().getBoolean(LOG_2XX_SETTING, false);
        var maxBytes = ctx.getConfig().getInteger(MAX_BODY_BYTES_SETTING, 16384);
        var redactRaw = ctx.getConfig().getString(REDACT_HEADERS_SETTING,
                "Authorization,Proxy-Authorization,Cookie,Set-Cookie");
        var redact = Arrays.stream(redactRaw.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        webService.registerResource(CONTEXT, new DspBodyLoggingFilter(monitor, log2xx, maxBytes, redact));
        monitor.info("[dsp-debug] filter registered on context '" + CONTEXT
                + "' (log2xx=" + log2xx + ", maxBytes=" + maxBytes
                + ", redactHeaders=" + redact + ")");
    }

    @Provider
    public static class DspBodyLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

        private static final String REQ_PATH_PROP = "dsif.dsp.req.path";

        private final Monitor monitor;
        private final boolean log2xx;
        private final int maxBytes;
        private final Set<String> redactHeaders;

        public DspBodyLoggingFilter(Monitor monitor, boolean log2xx, int maxBytes, Set<String> redactHeaders) {
            this.monitor = monitor;
            this.log2xx = log2xx;
            this.maxBytes = maxBytes;
            this.redactHeaders = redactHeaders;
        }

        @Override
        public void filter(ContainerRequestContext ctx) throws IOException {
            var path = ctx.getUriInfo().getPath();
            ctx.setProperty(REQ_PATH_PROP, path);

            // Read the entity stream into memory, log it, then re-deliver.
            byte[] bytes = new byte[0];
            var in = ctx.getEntityStream();
            if (in != null) {
                bytes = readAll(in);
                ctx.setEntityStream(new ByteArrayInputStream(bytes));
            }

            monitor.info("[dsp-debug] <-- " + ctx.getMethod() + " /" + path
                    + " (" + bytes.length + "-byte body)  [inbound request]");
            ctx.getHeaders().forEach((name, values) -> {
                var v = isRedacted(name) ? "<redacted>" : String.join(", ", values);
                monitor.info("[dsp-debug]     " + name + ": " + v);
            });
            if (bytes.length > 0) {
                monitor.info("[dsp-debug]     body: " + truncate(new String(bytes, StandardCharsets.UTF_8)));
            }
        }

        @Override
        public void filter(ContainerRequestContext req, ContainerResponseContext res) {
            var status = res.getStatus();
            if (status < 400 && !log2xx) {
                return;
            }
            var path = (String) req.getProperty(REQ_PATH_PROP);

            monitor.info("[dsp-debug] --> " + status + " /" + path + "  [outbound response]");
            res.getStringHeaders().forEach((name, values) -> {
                var v = isRedacted(name) ? "<redacted>" : String.join(", ", values);
                monitor.info("[dsp-debug]     " + name + ": " + v);
            });

            var entity = res.getEntity();
            String bodyPreview;
            if (entity == null) {
                bodyPreview = "<no entity>";
            } else if (entity instanceof byte[] b) {
                bodyPreview = truncate(new String(b, StandardCharsets.UTF_8));
            } else {
                // Most EDC responses are POJOs the JSON provider serialises later;
                // toString gives the field-level dump which is enough to spot the
                // reason string.
                bodyPreview = truncate(entity.toString());
            }
            monitor.info("[dsp-debug]     body: " + bodyPreview);
        }

        private boolean isRedacted(String name) {
            return name != null && redactHeaders.contains(name.toLowerCase(Locale.ROOT));
        }

        private byte[] readAll(InputStream in) throws IOException {
            try (var out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        }

        private String truncate(String s) {
            if (s == null) {
                return "<null>";
            }
            if (s.length() <= maxBytes) {
                return s;
            }
            return s.substring(0, maxBytes) + "…(" + (s.length() - maxBytes) + " more bytes)";
        }
    }
}
