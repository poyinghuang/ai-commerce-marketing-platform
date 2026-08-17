package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aicommerce.platform.delivery.application.port.*;
import com.aicommerce.platform.delivery.domain.*;

class PlatformOutcomeValidatorTest {
    private final PlatformOutcomeValidator validator = new PlatformOutcomeValidator();

    @Test
    void acceptsExactCreateAndMutationIdentityShapes() throws Exception {
        String external = "fake-campaign-0123456789abcdef01234567";
        var createEvidence=evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,
                Optional.of(hash(external)),Optional.of(PlatformObservedState.PAUSED),Optional.empty());
        var create=new WriteSucceeded(Optional.of(external),Optional.of("trace-1"),
                Optional.of(PlatformObservedState.PAUSED),createEvidence);
        assertThat(validator.validateWrite(PlatformOperationType.CREATE_CAMPAIGN,Optional.empty(),create)).isSameAs(create);

        var mutationEvidence=evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,
                Optional.empty(),Optional.empty(),Optional.empty());
        var mutation=new WriteSucceeded(Optional.empty(),Optional.empty(),Optional.empty(),mutationEvidence);
        assertThat(validator.validateWrite(PlatformOperationType.PAUSE,Optional.of(external),mutation)).isSameAs(mutation);
    }

    @Test
    void rejectsCrossedIdsAndFingerprintsForEverySuccessFamily() throws Exception {
        String external="fake-campaign-0123456789abcdef01234567";
        var mutationSuccess=new WriteSucceeded(Optional.empty(),Optional.empty(),Optional.empty(),
                evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,Optional.empty(),Optional.empty(),Optional.empty()));
        assertThatThrownBy(()->validator.validateWrite(PlatformOperationType.CREATE_CAMPAIGN,Optional.empty(),mutationSuccess))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PLATFORM_CONTRACT_INVALID");
        var wrongFingerprint=new WriteSucceeded(Optional.of(external),Optional.empty(),Optional.empty(),
                evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,Optional.of("0".repeat(64)),Optional.empty(),Optional.empty()));
        assertThatThrownBy(()->validator.validateWrite(PlatformOperationType.CREATE_CAMPAIGN,Optional.empty(),wrongFingerprint))
                .isInstanceOf(IllegalArgumentException.class);
        var found=new ReconciliationFound(Optional.of(external),Optional.empty(),Optional.empty(),
                evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FOUND,Optional.of(hash(external)),Optional.empty(),Optional.empty()));
        assertThatThrownBy(()->validator.validateReconciliation(PlatformOperationType.RESUME,Optional.of(external),found))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closedOutcomeConstructorsRejectContradictoryEvidence() {
        var terminal=evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty());
        assertThatThrownBy(()->new WriteUnknownOutcome(PlatformUnknownCode.PLATFORM_RESPONSE_AMBIGUOUS,Optional.empty(),terminal))
                .isInstanceOf(IllegalArgumentException.class);
        var found=evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FOUND,Optional.empty(),Optional.empty(),Optional.empty());
        assertThatThrownBy(()->new ReconciliationNotFound(Optional.empty(),found)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new WriteRetryableFailure(PlatformRetryableCode.PLATFORM_RATE_LIMITED,60,Optional.empty(),
                evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,Optional.empty(),Optional.empty(),Optional.of(30))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationGeneratedReconcileUnknownIsTheOnlyUnknownReconcileEvidenceShape() {
        var unknown=evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.UNKNOWN_OUTCOME,
                Optional.empty(),Optional.empty(),Optional.empty());
        assertThat(unknown.resultKind()).isEqualTo(PlatformEvidenceResultKind.UNKNOWN_OUTCOME);
        assertThatThrownBy(()->new ReconciliationStillUnknown(Optional.empty(),unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NormalizedPlatformEvidence evidence(PlatformAttemptKind kind,PlatformEvidenceResultKind result,
            Optional<String> fingerprint,Optional<PlatformObservedState> observed,Optional<Integer> retry){
        return new NormalizedPlatformEvidence(1,ProviderKey.FAKE,kind,result,fingerprint,observed,retry);
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
