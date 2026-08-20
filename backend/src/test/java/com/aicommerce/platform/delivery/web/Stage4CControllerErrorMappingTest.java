package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.aicommerce.platform.delivery.application.PlatformOperationException;
import com.aicommerce.platform.delivery.application.Stage4BViews;
import com.aicommerce.platform.delivery.application.Stage4CService;
import com.aicommerce.platform.delivery.application.Stage4CViews;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class Stage4CControllerErrorMappingTest {
    private final Stage4CService service = mock(Stage4CService.class);
    private final Stage4CController controller = new Stage4CController(service);

    @ParameterizedTest(name = "{0} on {3} -> {1} {2}")
    @MethodSource("operationErrors")
    void mapsEveryOperationSourceToStablePublicStatusCodeAndMessage(PlatformStableErrorCode source, HttpStatus status,
            String code, String uri) {
        var response = controller.operationError(new PlatformOperationException(source, Optional.empty()), request(uri));
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isEqualTo(expectedMessage(code));
        assertThat(response.getBody().fieldErrors()).isEmpty();
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("rateLimitedRoutes")
    void adMutationRoutesReturnExactSafe429HeadersAndOperationBody(String route, PlatformOperationType type,
            PlatformStableErrorCode code) throws Exception {
        UUID operation = UUID.nameUUIDFromBytes((route + code + "operation").getBytes());
        UUID entity = UUID.nameUUIDFromBytes((route + code + "entity").getBytes());
        UUID requestUuid = UUID.nameUUIDFromBytes((route + code + "request").getBytes());
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        var view = new Stage4BViews.Operation(operation, type, PlatformEntityType.AD, entity,
                PlatformOperationStatus.FAILED_RETRYABLE, 1, 0, 3, Optional.of(code), Optional.of(now.plusSeconds(60)),
                Optional.empty(), now, now, 7);
        var confirmation = new Stage4BViews.Confirmation(view, false);
        var http = request("/api/" + route);
        org.springframework.http.ResponseEntity<Stage4BViews.Operation> response = switch (route) {
            case "ad-create" -> {
                when(service.confirmCreate(entity, requestUuid, entity, entity, entity, entity, 0, "stage4c-error-test"))
                        .thenReturn(confirmation);
                yield controller.confirmCreate(entity, "W/\"0\"",
                        new Stage4CViews.AdCreateRequest(requestUuid, entity, entity, entity, entity), http);
            }
            case "ad-resume" -> {
                when(service.confirmState(entity, requestUuid, PlatformDesiredState.ACTIVE, 0, "stage4c-error-test"))
                        .thenReturn(confirmation);
                yield controller.adState(entity, "resume", "W/\"0\"",
                        new Stage4CViews.AdStateRequest(requestUuid, PlatformDesiredState.ACTIVE), http);
            }
            case "ad-pause" -> {
                when(service.confirmState(entity, requestUuid, PlatformDesiredState.PAUSED, 0, "stage4c-error-test"))
                        .thenReturn(confirmation);
                yield controller.adState(entity, "pause", "W/\"0\"",
                        new Stage4CViews.AdStateRequest(requestUuid, PlatformDesiredState.PAUSED), http);
            }
            default -> throw new AssertionError(route);
        };
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"7\"");
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/platform-operations/" + operation);
        var mapper = new tools.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(response.getBody());
        var body = mapper.readTree(json);
        assertThat(body.propertyNames()).doesNotContain("operation", "replay");
        assertThat(json).doesNotContain("completedAt").doesNotContain("evidence").doesNotContain("payload");
    }

    @ParameterizedTest(name = "{0} on {3} -> {1} {2}")
    @MethodSource("stageErrors")
    void mapsEveryStageExceptionToStablePublicStatusCodeMessageAndField(String source, HttpStatus status, String code,
            String uri, String field) {
        var response = controller.stageError(new com.aicommerce.platform.delivery.application.Stage4BException(source, status, field),
                request(uri));
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isEqualTo(expectedMessage(code));
        if (field == null) {
            assertThat(response.getBody().fieldErrors()).isEmpty();
        } else {
            assertThat(response.getBody().fieldErrors()).hasSize(1);
            assertThat(response.getBody().fieldErrors().get(0).field()).isEqualTo(field);
        }
        verifyNoInteractions(service);
    }

    static Stream<Arguments> operationErrors() {
        String create = "/api/platforms/meta/ad-sets/00000000-0000-4000-8000-000000000001/ads";
        String resume = "/api/platforms/meta/ads/00000000-0000-4000-8000-000000000001/resume";
        String pause = "/api/platforms/meta/ads/00000000-0000-4000-8000-000000000001/pause";
        String retry = "/api/platform-operations/00000000-0000-4000-8000-000000000001/retry";
        String reconcile = "/api/platform-operations/00000000-0000-4000-8000-000000000001/reconcile";
        return Stream.of(create, resume, pause, retry, reconcile).flatMap(uri -> Stream.of(
                mapped(PlatformStableErrorCode.PLATFORM_STALE_VERSION, uri),
                mapped(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID, uri),
                mapped(PlatformStableErrorCode.PLATFORM_PARENT_STATE_INVALID, uri),
                mapped(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID, uri),
                mapped(PlatformStableErrorCode.PLATFORM_OPERATION_NOT_FOUND, uri),
                mapped(PlatformStableErrorCode.PLATFORM_ACCOUNT_INACTIVE, uri),
                mapped(PlatformStableErrorCode.PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH, uri),
                mapped(PlatformStableErrorCode.PLATFORM_PROVIDER_UNSUPPORTED, uri),
                mapped(PlatformStableErrorCode.PLATFORM_ADAPTER_UNAVAILABLE, uri),
                mapped(PlatformStableErrorCode.PLATFORM_IDEMPOTENCY_CONFLICT, uri),
                mapped(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE, uri),
                mapped(PlatformStableErrorCode.PLATFORM_RETRY_NOT_DUE, uri),
                mapped(PlatformStableErrorCode.PLATFORM_MAX_ATTEMPTS_EXCEEDED, uri),
                mapped(PlatformStableErrorCode.PLATFORM_MAX_RECONCILIATIONS_EXCEEDED, uri),
                mapped(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED, uri)));
    }

    static Stream<Arguments> stageErrors() {
        String create = "/api/platforms/meta/ad-sets/00000000-0000-4000-8000-000000000001/ads";
        String adGet = "/api/platforms/meta/ads/00000000-0000-4000-8000-000000000001";
        String retry = "/api/platform-operations/00000000-0000-4000-8000-000000000001/retry";
        String reconcile = "/api/platform-operations/00000000-0000-4000-8000-000000000001/reconcile";
        return Stream.of(
                Arguments.of("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND, "PLATFORM_AD_NOT_FOUND", adGet, null),
                Arguments.of("PLATFORM_IF_MATCH_REQUIRED", HttpStatus.PRECONDITION_REQUIRED, "PLATFORM_IF_MATCH_REQUIRED", create, "If-Match"),
                Arguments.of("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "PLATFORM_REQUEST_INVALID", create, "query"),
                Arguments.of("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "PLATFORM_REQUEST_INVALID", create, "If-Match"),
                Arguments.of("PLATFORM_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "PLATFORM_REQUEST_INVALID", create, "body"),
                Arguments.of("PLATFORM_LEGACY_OPERATION_INERT", HttpStatus.CONFLICT, "PLATFORM_LEGACY_OPERATION_INERT", retry, null),
                Arguments.of("PLATFORM_LEGACY_OPERATION_INERT", HttpStatus.CONFLICT, "PLATFORM_LEGACY_OPERATION_INERT", reconcile, null),
                Arguments.of("PLATFORM_ENTITY_STALE", HttpStatus.PRECONDITION_FAILED, "PLATFORM_ENTITY_STALE", create, null),
                Arguments.of("PLATFORM_PARENT_STATE_INVALID", HttpStatus.CONFLICT, "PLATFORM_PARENT_STATE_INVALID", create, null),
                Arguments.of("PLATFORM_AD_EVIDENCE_INVALID", HttpStatus.CONFLICT, "PLATFORM_AD_EVIDENCE_INVALID", create, null));
    }

    private static Arguments mapped(PlatformStableErrorCode source, String uri) {
        boolean operationRoute = uri.startsWith("/api/platform-operations/");
        boolean createOrResume = uri.contains("/ads") && !uri.endsWith("/pause");
        String code = switch (source) {
            case PLATFORM_OPERATION_NOT_FOUND -> "PLATFORM_RESOURCE_NOT_FOUND";
            case PLATFORM_STALE_VERSION -> operationRoute ? "PLATFORM_OPERATION_STALE" : "PLATFORM_ENTITY_STALE";
            case PLATFORM_ACCOUNT_INACTIVE, PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH, PLATFORM_PROVIDER_UNSUPPORTED ->
                "PLATFORM_ACCOUNT_CONFIGURATION_INVALID";
            case PLATFORM_EVIDENCE_INVALID -> createOrResume ? "PLATFORM_AD_EVIDENCE_INVALID" : "PLATFORM_EVIDENCE_INVALID";
            case PLATFORM_PARENT_STATE_INVALID -> "PLATFORM_PARENT_STATE_INVALID";
            default -> source.name();
        };
        HttpStatus status = switch (code) {
            case "PLATFORM_CONTRACT_INVALID", "PLATFORM_REQUEST_INVALID" -> HttpStatus.BAD_REQUEST;
            case "PLATFORM_RESOURCE_NOT_FOUND", "PLATFORM_AD_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PLATFORM_ENTITY_STALE", "PLATFORM_OPERATION_STALE" -> HttpStatus.PRECONDITION_FAILED;
            case "PLATFORM_IF_MATCH_REQUIRED" -> HttpStatus.PRECONDITION_REQUIRED;
            case "PLATFORM_ADAPTER_UNAVAILABLE", "PLATFORM_ACCOUNT_CONFIGURATION_INVALID" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "PLATFORM_PROVIDER_RETRYABLE" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.CONFLICT;
        };
        return Arguments.of(source, status, code, uri);
    }

    @org.junit.jupiter.api.Test
    void sqlClaimConstraintMessagesMapToDistinctPublicCodes() {
        String create = "/api/platforms/meta/ad-sets/00000000-0000-4000-8000-000000000001/ads";
        String pause = "/api/platforms/meta/ads/00000000-0000-4000-8000-000000000001/pause";
        assertSql("23514", "ERROR: ct_platform_ad_submit_claim_stale", create, HttpStatus.PRECONDITION_FAILED, "PLATFORM_ENTITY_STALE");
        assertSql("23514", "ERROR: ct_platform_ad_submit_claim_parent_state", create, HttpStatus.CONFLICT, "PLATFORM_PARENT_STATE_INVALID");
        assertSql("23514", "ERROR: ct_platform_ad_submit_claim_evidence", create, HttpStatus.CONFLICT, "PLATFORM_AD_EVIDENCE_INVALID");
        assertSql("23514", "ERROR: ct_platform_ad_submit_claim_evidence", pause, HttpStatus.CONFLICT, "PLATFORM_EVIDENCE_INVALID");
        assertSql("40001", "could not serialize", create, HttpStatus.CONFLICT, "PLATFORM_LEDGER_CONCURRENCY_CONFLICT");
        assertSql("40P01", "deadlock detected", create, HttpStatus.CONFLICT, "PLATFORM_LEDGER_CONCURRENCY_CONFLICT");
    }

    private void assertSql(String state, String message, String uri, HttpStatus status, String code) {
        var exception = org.mockito.Mockito.mock(org.springframework.dao.DataAccessException.class);
        org.mockito.Mockito.when(exception.getMostSpecificCause()).thenReturn(new java.sql.SQLException(message, state));
        var response = controller.databaseError(exception, request(uri));
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isEqualTo(expectedMessage(code));
    }

    static Stream<Arguments> rateLimitedRoutes() {
        return Stream.of("ad-create", "ad-resume", "ad-pause")
                .flatMap(route -> Stream.of(PlatformStableErrorCode.PLATFORM_RATE_LIMITED,
                        PlatformStableErrorCode.PLATFORM_TEMPORARILY_UNAVAILABLE)
                        .map(code -> Arguments.of(route,
                                "ad-create".equals(route) ? PlatformOperationType.CREATE_AD
                                        : "ad-pause".equals(route) ? PlatformOperationType.PAUSE : PlatformOperationType.RESUME,
                                code)));
    }

    private static String expectedMessage(String code) {
        return switch (code) {
            case "PLATFORM_ENTITY_STALE" -> "The platform entity changed; reload and preview again";
            case "PLATFORM_AD_EVIDENCE_INVALID" -> "The approved Ad evidence is no longer eligible";
            case "PLATFORM_PARENT_STATE_INVALID" -> "The parent platform state does not allow this action";
            case "PLATFORM_EVIDENCE_INVALID" -> "Platform evidence is inconsistent";
            case "PLATFORM_CONTRACT_INVALID" -> "Platform contract is invalid";
            case "PLATFORM_RESOURCE_NOT_FOUND" -> "Platform resource was not found";
            case "PLATFORM_ACCOUNT_CONFIGURATION_INVALID" -> "The local platform account is unavailable";
            case "PLATFORM_ADAPTER_UNAVAILABLE" -> "The fake platform adapter is unavailable";
            case "PLATFORM_IDEMPOTENCY_CONFLICT" -> "The request conflicts with an existing operation";
            case "PLATFORM_INVALID_OPERATION_STATE" -> "The operation is not eligible for this action";
            case "PLATFORM_RETRY_NOT_DUE" -> "The operation is not yet eligible for retry";
            case "PLATFORM_MAX_ATTEMPTS_EXCEEDED" -> "The operation has no retry attempts remaining";
            case "PLATFORM_MAX_RECONCILIATIONS_EXCEEDED" -> "The operation has no reconciliation attempts remaining";
            case "PLATFORM_POLICY_REJECTED" -> "Platform policy rejected the request";
            case "PLATFORM_OPERATION_STALE" -> "The platform operation changed; reload and retry";
            case "PLATFORM_LEDGER_CONCURRENCY_CONFLICT" -> "The budget authorization changed concurrently";
            case "PLATFORM_AD_NOT_FOUND" -> "Platform Ad was not found";
            case "PLATFORM_IF_MATCH_REQUIRED" -> "If-Match is required";
            case "PLATFORM_REQUEST_INVALID" -> "Platform request is invalid";
            case "PLATFORM_LEGACY_OPERATION_INERT" -> "The legacy operation is read-only";
            default -> "The operation is not eligible for this action";
        };
    }

    private static MockHttpServletRequest request(String uri) {
        var request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setAttribute(com.aicommerce.platform.web.RequestIdFilter.REQUEST_ATTRIBUTE, "stage4c-error-test");
        return request;
    }
}
