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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Surfaces inbound DSP request bodies and 4xx/5xx response bodies in the
 * controlplane log.
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
 * <p>This filter intercepts the JAX-RS layer on the {@code protocol} web
 * context (the DSP HTTP endpoints, port 29194 by default) and:
 * <ul>
 *   <li>Logs the full body of every inbound request, with method/path/host.</li>
 *   <li>Logs status + body of every response with status &gt;= 400.</li>
 * </ul>
 *
 * <p>Configurable via:
 * <ul>
 *   <li>{@value #LOG_2XX_SETTING} — set to {@code true} to also log 2xx
 *       response bodies (heavyweight; off by default).</li>
 *   <li>{@value #MAX_BODY_BYTES_SETTING} — truncate logged bodies above this
 *       size. Default 16 KB.</li>
 * </ul>
 *
 * <p>Heads up: bodies on DSP endpoints can include serialised tokens. The
 * filter does not redact anything; treat the controlplane log as sensitive
 * while this is enabled, and turn it off once the bug is pinned down.
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

        webService.registerResource(CONTEXT, new DspBodyLoggingFilter(monitor, log2xx, maxBytes));
        monitor.info("[dsp-debug] Validation logging filter registered on context '" + CONTEXT
                + "' (log2xx=" + log2xx + ", maxBytes=" + maxBytes + ")");
    }

    @Provider
    public static class DspBodyLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

        private static final String REQ_BODY_PROP = "dsif.dsp.req.body";
        private static final String REQ_PATH_PROP = "dsif.dsp.req.path";

        private final Monitor monitor;
        private final boolean log2xx;
        private final int maxBytes;

        public DspBodyLoggingFilter(Monitor monitor, boolean log2xx, int maxBytes) {
            this.monitor = monitor;
            this.log2xx = log2xx;
            this.maxBytes = maxBytes;
        }

        @Override
        public void filter(ContainerRequestContext ctx) throws IOException {
            var path = ctx.getUriInfo().getPath();
            ctx.setProperty(REQ_PATH_PROP, path);

            // Read the entity stream into memory, log it, then re-deliver.
            var in = ctx.getEntityStream();
            if (in == null) {
                return;
            }
            var bytes = readAll(in);
            ctx.setEntityStream(new ByteArrayInputStream(bytes));
            ctx.setProperty(REQ_BODY_PROP, bytes);

            monitor.info("[dsp-debug] --> " + ctx.getMethod() + " /" + path
                    + " (" + bytes.length + "-byte body)");
            if (bytes.length > 0) {
                monitor.info("[dsp-debug]    " + truncate(new String(bytes, StandardCharsets.UTF_8)));
            }
        }

        @Override
        public void filter(ContainerRequestContext req, ContainerResponseContext res) {
            var status = res.getStatus();
            if (status < 400 && !log2xx) {
                return;
            }
            var path = (String) req.getProperty(REQ_PATH_PROP);
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

            monitor.info("[dsp-debug] <-- " + status + " /" + path + " body=" + bodyPreview);
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
