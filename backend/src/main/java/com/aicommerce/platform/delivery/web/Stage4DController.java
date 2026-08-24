package com.aicommerce.platform.delivery.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.delivery.application.Stage4BException;
import com.aicommerce.platform.delivery.application.Stage4DService;
import com.aicommerce.platform.delivery.application.Stage4DViews;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.ApiError;
import com.aicommerce.platform.web.error.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage4b.enabled:false}' == 'true' && '${platform.stage4d.enabled:false}' == 'true'")
@RequestMapping("/api/platform-entities")
public class Stage4DController {
    private static final Pattern ASOF = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$");
    private final Stage4DService service;
    private final Clock clock;

    public Stage4DController(Stage4DService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/{entityType}/{entityUuid}/delivery")
    public ResponseEntity<Stage4DViews.DeliveryView> delivery(@PathVariable String entityType, @PathVariable UUID entityUuid) {
        var value = service.delivery(type(entityType), entityUuid);
        return ResponseEntity.ok().eTag(ResourceEtag.format(value.version())).body(value);
    }

    @PostMapping("/{entityType}/{entityUuid}/delivery-sync/preview")
    public Stage4DViews.DeliveryPreview previewDelivery(@PathVariable String entityType, @PathVariable UUID entityUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        empty(ifMatch);
        return service.previewDelivery(type(entityType), entityUuid);
    }

    @PostMapping("/{entityType}/{entityUuid}/delivery-sync")
    public ResponseEntity<Stage4DViews.DeliveryView> syncDelivery(@PathVariable String entityType, @PathVariable UUID entityUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, HttpServletRequest request) {
        empty(ifMatch);
        var value = service.syncDelivery(type(entityType), entityUuid, requestId(request));
        return ResponseEntity.ok().eTag(ResourceEtag.format(value.version())).body(value);
    }

    @GetMapping("/{entityType}/{entityUuid}/metrics")
    public Stage4DViews.MetricsView metrics(@PathVariable String entityType, @PathVariable UUID entityUuid,
            HttpServletRequest request) {
        return service.metrics(type(entityType), entityUuid, asOf(request));
    }

    @PostMapping("/{entityType}/{entityUuid}/metrics-refresh/preview")
    public Stage4DViews.MetricsPreview previewMetrics(@PathVariable String entityType, @PathVariable UUID entityUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        empty(ifMatch);
        return service.previewMetrics(type(entityType), entityUuid);
    }

    @PostMapping("/{entityType}/{entityUuid}/metrics-refresh")
    public Stage4DViews.MetricsView refreshMetrics(@PathVariable String entityType, @PathVariable UUID entityUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        empty(ifMatch);
        return service.refreshMetrics(type(entityType), entityUuid);
    }

    @ExceptionHandler(Stage4BException.class)
    ResponseEntity<ApiError> stageError(Stage4BException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(error(ex.code(), request, ex.field()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseError(DataAccessException ex, HttpServletRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        String state = cause instanceof java.sql.SQLException sql ? sql.getSQLState() : null;
        if ("40001".equals(state) || "40P01".equals(state)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PLATFORM_REFRESH_CONCURRENCY_CONFLICT", request));
        }
        throw ex;
    }

    private static PlatformEntityType type(String token) {
        try {
            return PlatformEntityType.valueOf(token);
        } catch (IllegalArgumentException exception) {
            throw new Stage4BException("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "path");
        }
    }

    private Optional<Instant> asOf(HttpServletRequest request) {
        String[] values = request.getParameterValues("asOf");
        if (values == null || values.length == 0) return Optional.empty();
        if (values.length != 1 || !ASOF.matcher(values[0]).matches()) {
            throw new Stage4BException("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        Instant parsed = Instant.parse(values[0]);
        if (parsed.isAfter(Instant.now(clock))) {
            throw new Stage4BException("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        return Optional.of(parsed);
    }

    private static void empty(String ifMatch) {
        if (ifMatch != null) throw new Stage4BException("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "If-Match");
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }

    private static ApiError error(String code, HttpServletRequest request) {
        return error(code, request, null);
    }

    private static ApiError error(String code, HttpServletRequest request, String field) {
        List<FieldErrorDetail> fields = field == null ? List.of() : List.of(new FieldErrorDetail(field,
                "If-Match".equals(field) ? "Invalid If-Match"
                        : "body".equals(field) ? "Invalid request body"
                        : "query".equals(field) ? "Query parameters are not allowed"
                        : "path".equals(field) ? "Invalid path" : "Invalid value"));
        return new ApiError(code, message(code), requestId(request), Instant.now(), request.getRequestURI(), fields);
    }

    private static String message(String code) {
        return switch (code) {
            case "PLATFORM_REQUEST_INVALID" -> "Platform request is invalid";
            case "PLATFORM_CONTRACT_INVALID" -> "Platform contract is invalid";
            case "PLATFORM_RESOURCE_NOT_FOUND" -> "Platform resource was not found";
            case "PLATFORM_ENTITY_ARCHIVED" -> "The platform entity is archived";
            case "PLATFORM_DELIVERY_NOT_SYNCABLE" -> "The platform entity has no durable external id";
            case "PLATFORM_REFRESH_CONCURRENCY_CONFLICT" -> "The refresh could not be stored; retry";
            case "PLATFORM_ACCOUNT_CONFIGURATION_INVALID" -> "The local platform account is unavailable";
            case "PLATFORM_ADAPTER_UNAVAILABLE" -> "The fake platform adapter is unavailable";
            default -> "The operation is not eligible for this action";
        };
    }
}
