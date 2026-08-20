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

    static Stream<Arguments> operationErrors() {
        String create = "/api/platforms/meta/ad-sets/00000000-0000-4000-8000-000000000001/ads";
        String resume = "/api/platforms/meta/ads/00000000-0000-4000-8000-000000000001/resume";
        String pause = "/api/platforms/meta/ads/00000000-0000-4000-8000-000000000001/pause";
        return Stream.of(
                Arguments.of(PlatformStableErrorCode.PLATFORM_STALE_VERSION, HttpStatus.PRECONDITION_FAILED, "PLATFORM_ENTITY_STALE", create),
                Arguments.of(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID, HttpStatus.CONFLICT, "PLATFORM_AD_EVIDENCE_INVALID", create),
                Arguments.of(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID, HttpStatus.CONFLICT, "PLATFORM_AD_EVIDENCE_INVALID", resume),
                Arguments.of(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID, HttpStatus.CONFLICT, "PLATFORM_EVIDENCE_INVALID", pause),
                Arguments.of(PlatformStableErrorCode.PLATFORM_PARENT_STATE_INVALID, HttpStatus.CONFLICT, "PLATFORM_PARENT_STATE_INVALID", create),
                Arguments.of(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID, HttpStatus.BAD_REQUEST, "PLATFORM_CONTRACT_INVALID", create));
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
            default -> code;
        };
    }

    private static MockHttpServletRequest request(String uri) {
        var request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setAttribute(com.aicommerce.platform.web.RequestIdFilter.REQUEST_ATTRIBUTE, "stage4c-error-test");
        return request;
    }
}
