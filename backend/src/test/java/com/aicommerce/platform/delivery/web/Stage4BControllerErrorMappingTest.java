package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Stream;

import com.aicommerce.platform.delivery.application.PlatformOperationException;
import com.aicommerce.platform.delivery.application.Stage4BService;
import com.aicommerce.platform.delivery.application.Stage4BViews;
import com.aicommerce.platform.delivery.domain.*;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class Stage4BControllerErrorMappingTest {
    private final Stage4BService service=mock(Stage4BService.class);
    private final Stage4BController controller=new Stage4BController(service);

    @ParameterizedTest(name="{0} -> {1} {2}")
    @MethodSource("operationErrors")
    void mapsEveryOperationSourceToStablePublicStatusCodeAndMessage(PlatformStableErrorCode source,HttpStatus status,String code) {
        var request=request("/api/platform-operations/00000000-0000-4000-8000-000000000001/retry");
        var response=controller.operationError(new PlatformOperationException(source,Optional.empty()),request);
        assertThat(response.getStatusCode()).isEqualTo(status);assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isNotBlank().doesNotContainIgnoringCase("provider");
        assertThat(response.getBody().fieldErrors()).isEmpty();verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("databaseStates")
    void mapsSerializationAndDeadlockWithoutCallingTheService(String sqlState) {
        var failure=new DataIntegrityViolationException("safe",new SQLException("sentinel secret",sqlState));
        var response=controller.databaseError(failure,request("/api/platforms/meta/ad-sets/00000000-0000-4000-8000-000000000001/budget"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(response.getBody().code()).isEqualTo("PLATFORM_LEDGER_CONCURRENCY_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("The budget authorization changed concurrently").doesNotContain("sentinel");verifyNoInteractions(service);
    }

    @ParameterizedTest(name="{0} exposes route-specific normalized {3} as safe 429")
    @MethodSource("rateLimitedRoutes")
    void everyMutationAndRecoveryRouteReturnsExactSafe429HeadersAndOperationBody(String route,PlatformOperationType operationType,PlatformEntityType entityType,PlatformStableErrorCode code) throws Exception {
        UUID operation=UUID.nameUUIDFromBytes((route+code+"operation").getBytes(java.nio.charset.StandardCharsets.UTF_8)),entity=UUID.nameUUIDFromBytes((route+code+"entity").getBytes(java.nio.charset.StandardCharsets.UTF_8)),requestUuid=UUID.nameUUIDFromBytes((route+code+"request").getBytes(java.nio.charset.StandardCharsets.UTF_8));Instant now=Instant.parse("2026-08-17T00:00:00Z");
        var view=new Stage4BViews.Operation(operation,operationType,entityType,entity,PlatformOperationStatus.FAILED_RETRYABLE,1,0,3,Optional.of(code),Optional.of(now.plusSeconds(60)),Optional.empty(),now,now,7);
        var confirmation=new Stage4BViews.Confirmation(view,false);var request=request("/api/"+route);
        org.springframework.http.ResponseEntity<Stage4BViews.Operation> response=switch(route){
            case "campaign-create"->{when(service.confirmCampaign(requestUuid,entity,0,"stage4b-error-test")).thenReturn(confirmation);yield controller.confirmCampaign(new Stage4BController.CampaignConfirmRequest(requestUuid,entity,0L),request);}
            case "adset-create"->{when(service.confirmAdSet(entity,requestUuid,PlatformBudgetType.DAILY,"25",0,0,"stage4b-error-test")).thenReturn(confirmation);yield controller.confirmAdSet(entity,"W/\"0\"",new Stage4BController.AdSetConfirmRequest(requestUuid,PlatformBudgetType.DAILY,"25",0L),request);}
            case "campaign-state"->{when(service.confirmState(PlatformEntityType.CAMPAIGN,entity,requestUuid,PlatformDesiredState.ACTIVE,0,"stage4b-error-test")).thenReturn(confirmation);yield controller.campaignState(entity,"resume","W/\"0\"",new Stage4BController.StateMutationRequest(requestUuid,PlatformDesiredState.ACTIVE),request);}
            case "adset-state"->{when(service.confirmState(PlatformEntityType.AD_SET,entity,requestUuid,PlatformDesiredState.ACTIVE,0,"stage4b-error-test")).thenReturn(confirmation);yield controller.adSetState(entity,"resume","W/\"0\"",new Stage4BController.StateMutationRequest(requestUuid,PlatformDesiredState.ACTIVE),request);}
            case "budget"->{when(service.confirmBudget(entity,requestUuid,"20",0,"stage4b-error-test")).thenReturn(confirmation);yield controller.budget(entity,"W/\"0\"",new Stage4BController.BudgetMutationRequest(requestUuid,"20"),request);}
            case "retry"->{when(service.retry(operation,0)).thenReturn(confirmation);yield controller.retry(operation,"W/\"0\"",null);}
            case "reconcile"->{when(service.reconcile(operation,0)).thenReturn(confirmation);yield controller.reconcile(operation,"W/\"0\"",null);}
            default->throw new AssertionError(route);
        };
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"7\"");assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/platform-operations/"+operation);
        var mapper=new tools.jackson.databind.ObjectMapper();String json=mapper.writeValueAsString(response.getBody());var body=mapper.readTree(json);
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("operationUuid","operationType","entityType","entityUuid","status","attemptCount","reconciliationCount","maxAttempts","normalizedErrorCode","nextAttemptAt","createdAt","updatedAt","version");
        assertThat(body.get("operationUuid").asText()).isEqualTo(operation.toString());assertThat(body.get("operationType").asText()).isEqualTo(operationType.name());assertThat(body.get("entityType").asText()).isEqualTo(entityType.name());assertThat(body.get("entityUuid").asText()).isEqualTo(entity.toString());assertThat(body.get("status").asText()).isEqualTo("FAILED_RETRYABLE");assertThat(body.get("normalizedErrorCode").asText()).isEqualTo(code.name());assertThat(body.get("attemptCount").asInt()).isEqualTo(1);assertThat(body.get("reconciliationCount").asInt()).isZero();assertThat(body.get("maxAttempts").asInt()).isEqualTo(3);assertThat(body.get("nextAttemptAt").asText()).isEqualTo("2026-08-17T00:01:00Z");assertThat(body.get("createdAt").asText()).isEqualTo("2026-08-17T00:00:00Z");assertThat(body.get("updatedAt").asText()).isEqualTo("2026-08-17T00:00:00Z");assertThat(body.get("version").asLong()).isEqualTo(7);
        assertThat(json).doesNotContainIgnoringCase("secret").doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("credential").doesNotContainIgnoringCase("authorization").doesNotContainIgnoringCase("cookie").doesNotContain("http://").doesNotContain("https://").doesNotContain("completedAt").doesNotContain("evidence").doesNotContain("payload");
    }

    static Stream<Arguments> operationErrors(){return Stream.of(
        Arguments.of(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID,HttpStatus.BAD_REQUEST,"PLATFORM_CONTRACT_INVALID"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_OPERATION_NOT_FOUND,HttpStatus.NOT_FOUND,"PLATFORM_RESOURCE_NOT_FOUND"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_STALE_VERSION,HttpStatus.PRECONDITION_FAILED,"PLATFORM_OPERATION_STALE"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_ACCOUNT_INACTIVE,HttpStatus.SERVICE_UNAVAILABLE,"PLATFORM_ACCOUNT_CONFIGURATION_INVALID"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH,HttpStatus.SERVICE_UNAVAILABLE,"PLATFORM_ACCOUNT_CONFIGURATION_INVALID"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_PROVIDER_UNSUPPORTED,HttpStatus.SERVICE_UNAVAILABLE,"PLATFORM_ACCOUNT_CONFIGURATION_INVALID"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_ADAPTER_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE,"PLATFORM_ADAPTER_UNAVAILABLE"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_RETRY_NOT_DUE,HttpStatus.CONFLICT,"PLATFORM_RETRY_NOT_DUE"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_RECOVERY_NOT_DUE,HttpStatus.CONFLICT,"PLATFORM_RECOVERY_NOT_DUE"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_MAX_ATTEMPTS_EXCEEDED,HttpStatus.CONFLICT,"PLATFORM_MAX_ATTEMPTS_EXCEEDED"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_MAX_RECONCILIATIONS_EXCEEDED,HttpStatus.CONFLICT,"PLATFORM_MAX_RECONCILIATIONS_EXCEEDED"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED,HttpStatus.CONFLICT,"PLATFORM_POLICY_REJECTED"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_IDEMPOTENCY_CONFLICT,HttpStatus.CONFLICT,"PLATFORM_IDEMPOTENCY_CONFLICT"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID,HttpStatus.CONFLICT,"PLATFORM_EVIDENCE_INVALID"),
        Arguments.of(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE,HttpStatus.CONFLICT,"PLATFORM_INVALID_OPERATION_STATE"));}
    static Stream<String> databaseStates(){return Stream.of("40001","40P01");}
    static Stream<Arguments> rateLimitedRoutes(){return Stream.of(
        Arguments.of("campaign-create",PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN),
        Arguments.of("adset-create",PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET),
        Arguments.of("campaign-state",PlatformOperationType.RESUME,PlatformEntityType.CAMPAIGN),
        Arguments.of("adset-state",PlatformOperationType.RESUME,PlatformEntityType.AD_SET),
        Arguments.of("budget",PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET),
        Arguments.of("retry",PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN),
        Arguments.of("reconcile",PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET)
    ).flatMap(route->Stream.of(PlatformStableErrorCode.PLATFORM_RATE_LIMITED,PlatformStableErrorCode.PLATFORM_TEMPORARILY_UNAVAILABLE).map(code->Arguments.of(route.get()[0],route.get()[1],route.get()[2],code)));}
    private static MockHttpServletRequest request(String uri){var request=new MockHttpServletRequest();request.setRequestURI(uri);request.setAttribute(com.aicommerce.platform.web.RequestIdFilter.REQUEST_ATTRIBUTE,"stage4b-error-test");return request;}
}
