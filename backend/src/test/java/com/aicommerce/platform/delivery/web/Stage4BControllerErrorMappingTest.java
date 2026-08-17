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

    @ParameterizedTest(name="{0} exposes normalized {1} as safe 429")
    @MethodSource("rateLimitedRoutes")
    void everyMutationAndRecoveryRouteReturnsExactSafe429HeadersAndOperationBody(String route,PlatformStableErrorCode code) throws Exception {
        UUID operation=UUID.randomUUID(),entity=UUID.randomUUID(),requestUuid=UUID.randomUUID();Instant now=Instant.parse("2026-08-17T00:00:00Z");
        var view=new Stage4BViews.Operation(operation,PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,entity,PlatformOperationStatus.FAILED_RETRYABLE,1,0,3,Optional.of(code),Optional.of(now.plusSeconds(60)),Optional.empty(),now,now,7);
        var confirmation=new Stage4BViews.Confirmation(view,false);var request=request("/api/"+route);
        org.springframework.http.ResponseEntity<Stage4BViews.Operation> response=switch(route){
            case "create"->{when(service.confirmCampaign(requestUuid,entity,0,"stage4b-error-test")).thenReturn(confirmation);yield controller.confirmCampaign(new Stage4BController.CampaignConfirmRequest(requestUuid,entity,0L),request);}
            case "state"->{when(service.confirmState(PlatformEntityType.AD_SET,entity,requestUuid,PlatformDesiredState.ACTIVE,0,"stage4b-error-test")).thenReturn(confirmation);yield controller.adSetState(entity,"resume","W/\"0\"",new Stage4BController.StateMutationRequest(requestUuid,PlatformDesiredState.ACTIVE),request);}
            case "budget"->{when(service.confirmBudget(entity,requestUuid,"20",0,"stage4b-error-test")).thenReturn(confirmation);yield controller.budget(entity,"W/\"0\"",new Stage4BController.BudgetMutationRequest(requestUuid,"20"),request);}
            case "retry"->{when(service.retry(operation,0)).thenReturn(confirmation);yield controller.retry(operation,"W/\"0\"",null);}
            case "reconcile"->{when(service.reconcile(operation,0)).thenReturn(confirmation);yield controller.reconcile(operation,"W/\"0\"",null);}
            default->throw new AssertionError(route);
        };
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"7\"");assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/platform-operations/"+operation);
        String json=new tools.jackson.databind.ObjectMapper().writeValueAsString(response.getBody());
        assertThat(json).contains("\"normalizedErrorCode\":\""+code.name()+"\"").contains("\"status\":\"FAILED_RETRYABLE\"")
            .doesNotContainIgnoringCase("secret").doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("credential")
            .doesNotContainIgnoringCase("authorization").doesNotContainIgnoringCase("cookie").doesNotContain("http://").doesNotContain("https://");
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
    static Stream<Arguments> rateLimitedRoutes(){return Stream.of("create","state","budget","retry","reconcile").flatMap(route->Stream.of(PlatformStableErrorCode.PLATFORM_RATE_LIMITED,PlatformStableErrorCode.PLATFORM_TEMPORARILY_UNAVAILABLE).map(code->Arguments.of(route,code)));}
    private static MockHttpServletRequest request(String uri){var request=new MockHttpServletRequest();request.setRequestURI(uri);request.setAttribute(com.aicommerce.platform.web.RequestIdFilter.REQUEST_ATTRIBUTE,"stage4b-error-test");return request;}
}
