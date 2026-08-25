package com.aicommerce.platform.dashboard.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aicommerce.platform.dashboard.application.DashboardException;
import com.aicommerce.platform.dashboard.application.DashboardService;
import com.aicommerce.platform.dashboard.application.DashboardViews;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.ApiError;
import com.aicommerce.platform.web.error.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage5.enabled:false}' == 'true'")
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final Set<String> SECTIONS = Set.of(
            "todos", "products", "reviews", "campaigns", "platform-campaigns", "anomalies");
    private static final Set<String> PAGE_KEYS = Set.of("page", "size");
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<DashboardViews.DashboardView> summary(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DashboardException("DASHBOARD_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary());
    }

    @GetMapping("/{section}")
    public ResponseEntity<DashboardViews.DashboardPageView<?>> page(
            @PathVariable String section, HttpServletRequest request) {
        if (!SECTIONS.contains(section)) {
            throw new DashboardException("DASHBOARD_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "section");
        }
        Pagination pagination = pagination(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.page(section, pagination.page(), pagination.size()));
    }

    @ExceptionHandler(DashboardException.class)
    ResponseEntity<ApiError> dashboardError(DashboardException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(error(exception.code(), request, exception.field()));
    }

    private static Pagination pagination(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        for (var entry : params.entrySet()) {
            if (!PAGE_KEYS.contains(entry.getKey()) || entry.getValue().length != 1) {
                throw new DashboardException("DASHBOARD_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "query");
            }
        }
        int page = integer(params, "page", 0, 0, Integer.MAX_VALUE);
        int size = integer(params, "size", 20, 1, 100);
        return new Pagination(page, size);
    }

    private static int integer(Map<String, String[]> params, String key, int fallback, int min, int max) {
        if (!params.containsKey(key)) return fallback;
        String raw = params.get(key)[0];
        if (raw == null || !raw.matches("^(0|[1-9][0-9]*)$")) {
            throw new DashboardException("DASHBOARD_REQUEST_INVALID", HttpStatus.BAD_REQUEST, key);
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new DashboardException("DASHBOARD_REQUEST_INVALID", HttpStatus.BAD_REQUEST, key);
        }
        if (value < min || value > max) {
            throw new DashboardException("DASHBOARD_REQUEST_INVALID", HttpStatus.BAD_REQUEST, key);
        }
        return value;
    }

    private static ApiError error(String code, HttpServletRequest request, String field) {
        List<FieldErrorDetail> fields = field == null ? List.of()
                : List.of(new FieldErrorDetail(field, "Invalid value"));
        return new ApiError(code, message(code), requestId(request), Instant.now(), request.getRequestURI(), fields);
    }

    private static String message(String code) {
        return switch (code) {
            case "DASHBOARD_REQUEST_INVALID" -> "Dashboard request is invalid";
            case "DASHBOARD_DISABLED" -> "Dashboard is unavailable";
            case "PLATFORM_ACCOUNT_CONFIGURATION_INVALID" -> "The local platform account is unavailable";
            default -> "Dashboard request is invalid";
        };
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }

    private record Pagination(int page, int size) {}
}
