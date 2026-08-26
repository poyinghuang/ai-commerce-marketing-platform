package com.aicommerce.platform.decision.web;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.decision.application.DecisionException;
import com.aicommerce.platform.decision.application.DecisionService;
import com.aicommerce.platform.decision.application.DecisionViews;
import com.aicommerce.platform.decision.application.DecisionViews.RecommendationStatus;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.ApiError;
import com.aicommerce.platform.web.error.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage6.enabled:false}' == 'true'")
@RequestMapping("/api/decision-recommendations")
public class DecisionController {
    private static final Set<String> LIST_KEYS = Set.of("page", "size", "status");
    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ResponseEntity<DecisionViews.GenerateView> generate(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        return ok(service.generate(requestId(request)));
    }

    @GetMapping
    public ResponseEntity<DecisionViews.DecisionPageView> list(HttpServletRequest request) {
        Pagination pagination = pagination(request);
        return ok(service.list(pagination.page(), pagination.size(), pagination.status()));
    }

    @GetMapping("/{recommendationUuid}")
    public ResponseEntity<DecisionViews.RecommendationDetailView> detail(
            @PathVariable UUID recommendationUuid, HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        requireCanonicalUuid(recommendationUuid, request);
        var value = service.detail(recommendationUuid);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(ResourceEtag.format(value.version()))
                .body(value);
    }

    @PostMapping("/{recommendationUuid}/approve")
    public ResponseEntity<DecisionViews.RecommendationDetailView> approve(@PathVariable UUID recommendationUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        requireCanonicalUuid(recommendationUuid, request);
        if (body != null && (!body.isObject() || !body.isEmpty())) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "body");
        }
        var value = service.approve(recommendationUuid, version(ifMatch), requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(ResourceEtag.format(value.version()))
                .body(value);
    }

    @PostMapping("/{recommendationUuid}/reject")
    public ResponseEntity<DecisionViews.RecommendationDetailView> reject(@PathVariable UUID recommendationUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody JsonNode body, HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        requireCanonicalUuid(recommendationUuid, request);
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("reason") || !body.get("reason").isString()) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "body");
        }
        var value = service.reject(recommendationUuid, version(ifMatch), body.get("reason").asText(), requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(ResourceEtag.format(value.version()))
                .body(value);
    }

    @ExceptionHandler(DecisionException.class)
    ResponseEntity<ApiError> decisionError(DecisionException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(error(exception.code(), request, exception.field()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseError(DataAccessException exception, HttpServletRequest request) {
        Throwable cause = exception.getMostSpecificCause();
        String state = cause instanceof java.sql.SQLException sql ? sql.getSQLState() : null;
        if ("40001".equals(state) || "40P01".equals(state)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("DECISION_CONCURRENCY_CONFLICT", request, null));
        }
        throw exception;
    }

    private static Pagination pagination(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        for (var entry : params.entrySet()) {
            if (!LIST_KEYS.contains(entry.getKey()) || entry.getValue().length != 1) {
                throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
            }
        }
        int page = integer(params, "page", 0, 0, Integer.MAX_VALUE);
        int size = integer(params, "size", 20, 1, 100);
        RecommendationStatus status = RecommendationStatus.PENDING;
        if (params.containsKey("status")) {
            String raw = params.get("status")[0];
            try {
                status = RecommendationStatus.valueOf(raw);
            } catch (IllegalArgumentException exception) {
                throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "status");
            }
            if (status != RecommendationStatus.PENDING && status != RecommendationStatus.APPROVED
                    && status != RecommendationStatus.REJECTED) {
                throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "status");
            }
        }
        return new Pagination(page, size, status);
    }

    private static int integer(Map<String, String[]> params, String key, int fallback, int min, int max) {
        if (!params.containsKey(key)) return fallback;
        String raw = params.get(key)[0];
        if (raw == null || !raw.matches("^(0|[1-9][0-9]*)$")) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, key);
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, key);
        }
        if (value < min || value > max) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, key);
        }
        return value;
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new DecisionException("DECISION_PRECONDITION_REQUIRED", HttpStatus.PRECONDITION_REQUIRED, "If-Match");
        }
        try {
            return ResourceEtag.parse(ifMatch);
        } catch (IllegalArgumentException exception) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "If-Match");
        }
    }

    private static void requireCanonicalUuid(UUID value, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String canonical = value.toString().toLowerCase(Locale.ROOT);
        if (!uri.contains(canonical)) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "path");
        }
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static ApiError error(String code, HttpServletRequest request, String field) {
        List<FieldErrorDetail> fields = field == null ? List.of()
                : List.of(new FieldErrorDetail(field, "If-Match".equals(field) ? "Invalid If-Match"
                        : "body".equals(field) ? "Invalid request body"
                        : "query".equals(field) ? "Query parameters are not allowed"
                        : "Invalid value"));
        return new ApiError(code, message(code), requestId(request), Instant.now(), request.getRequestURI(), fields);
    }

    private static String message(String code) {
        return switch (code) {
            case "DECISION_REQUEST_INVALID" -> "Decision request is invalid";
            case "DECISION_DISABLED" -> "Decision engine is unavailable";
            case "DECISION_NOT_FOUND" -> "Decision recommendation was not found";
            case "DECISION_ALREADY_DECIDED" -> "Decision recommendation is already decided";
            case "DECISION_CONCURRENCY_CONFLICT" -> "Decision recommendation could not be updated";
            case "DECISION_STALE" -> "Decision recommendation version is stale";
            case "DECISION_PRECONDITION_REQUIRED" -> "If-Match is required";
            case "PLATFORM_ACCOUNT_CONFIGURATION_INVALID" -> "The local platform account is unavailable";
            default -> "Decision request is invalid";
        };
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }

    private record Pagination(int page, int size, RecommendationStatus status) {}
}
