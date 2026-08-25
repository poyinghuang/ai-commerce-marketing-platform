package com.aicommerce.platform.decision.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage6.enabled:false}' == 'true'")
final class DecisionRequestBoundaryFilter extends OncePerRequestFilter {
    static final int MAX_REQUEST_BYTES = 16 * 1024;
    private static final Pattern UUID_TOKEN = Pattern.compile(
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[1-5][0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/decision-recommendations");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.contains("..") || path.contains("//") || path.contains("%")) {
            reject(response, request, "query");
            return;
        }
        Matcher pathUuid = UUID_TOKEN.matcher(path);
        while (pathUuid.find()) {
            if (!pathUuid.group().equals(pathUuid.group().toLowerCase(Locale.ROOT))) {
                reject(response, request, "path");
                return;
            }
        }
        boolean generate = path.equals("/api/decision-recommendations/generate");
        boolean list = path.equals("/api/decision-recommendations");
        boolean item = path.matches("/api/decision-recommendations/"
                + "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        boolean mutate = path.matches("/api/decision-recommendations/"
                + "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/(approve|reject)");
        if (!generate && !list && !item && !mutate) {
            reject(response, request, "path");
            return;
        }
        if (generate) {
            if (!"POST".equals(request.getMethod()) || request.getQueryString() != null
                    || request.getContentType() != null || request.getHeader("If-Match") != null) {
                reject(response, request, generateField(request));
                return;
            }
        }
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            response.setStatus(413);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"DECISION_REQUEST_INVALID\",\"message\":\"Decision request is invalid\"}");
            return;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        if (generate && !text.isBlank()) {
            reject(response, request, "body");
            return;
        }
        if (("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) && !text.isBlank()) {
            reject(response, request, "body");
            return;
        }
        if (mutate) {
            boolean rejectPath = path.endsWith("/reject");
            String contentType = request.getContentType();
            if (rejectPath && !MediaType.APPLICATION_JSON_VALUE.equals(contentType)) {
                reject(response, request, "body");
                return;
            }
            if (!rejectPath && contentType != null && !MediaType.APPLICATION_JSON_VALUE.equals(contentType)) {
                reject(response, request, "body");
                return;
            }
        }
        chain.doFilter(new CachedRequest(request, body), response);
    }

    private static String generateField(HttpServletRequest request) {
        if (request.getQueryString() != null) return "query";
        if (request.getHeader("If-Match") != null) return "If-Match";
        return "body";
    }

    private static void reject(HttpServletResponse response, HttpServletRequest request, String field)
            throws IOException {
        response.setStatus(400);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId = String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
        String fieldErrors = "[{\"field\":\"" + field + "\",\"message\":\"" + fieldMessage(field) + "\"}]";
        response.getWriter().write("{\"code\":\"DECISION_REQUEST_INVALID\",\"message\":\"Decision request is invalid\","
                + "\"requestId\":\"" + requestId + "\",\"timestamp\":\"" + Instant.now() + "\",\"path\":\""
                + request.getRequestURI() + "\",\"fieldErrors\":" + fieldErrors + "}");
    }

    private static String fieldMessage(String field) {
        return switch (field) {
            case "query" -> "Query parameters are not allowed";
            case "path" -> "Invalid path";
            case "If-Match" -> "Invalid If-Match";
            case "body" -> "Invalid request body";
            default -> "Invalid value";
        };
    }

    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    try {
                        if (input.available() > 0) listener.onDataAvailable();
                        if (input.available() == 0) listener.onAllDataRead();
                    } catch (IOException e) {
                        listener.onError(e);
                    }
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }
    }
}
