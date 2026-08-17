package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import com.aicommerce.platform.delivery.application.PlatformOperationException;
import com.aicommerce.platform.delivery.application.Stage4BService;
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
    private static MockHttpServletRequest request(String uri){var request=new MockHttpServletRequest();request.setRequestURI(uri);request.setAttribute("requestId","stage4b-error-test");return request;}
}
