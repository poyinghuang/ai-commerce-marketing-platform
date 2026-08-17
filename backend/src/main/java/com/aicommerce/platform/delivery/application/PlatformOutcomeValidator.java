package com.aicommerce.platform.delivery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.aicommerce.platform.delivery.application.port.PlatformReconciliationOutcome;
import com.aicommerce.platform.delivery.application.port.PlatformWriteOutcome;
import com.aicommerce.platform.delivery.application.port.ReconciliationFound;
import com.aicommerce.platform.delivery.application.port.WriteSucceeded;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;

@Component
public class PlatformOutcomeValidator {

    public PlatformWriteOutcome validateWrite(PlatformOperationType operationType,
            Optional<String> durableEntityExternalId, PlatformWriteOutcome outcome) {
        require(operationType, durableEntityExternalId, outcome);
        if (outcome instanceof WriteSucceeded success) {
            validateSuccessIdentity(operationType, durableEntityExternalId, success.externalId(),
                    success.evidence().externalIdFingerprint());
        }
        return outcome;
    }

    public PlatformReconciliationOutcome validateReconciliation(PlatformOperationType operationType,
            Optional<String> durableEntityExternalId, PlatformReconciliationOutcome outcome) {
        require(operationType, durableEntityExternalId, outcome);
        if (outcome instanceof ReconciliationFound found) {
            validateSuccessIdentity(operationType, durableEntityExternalId, found.externalId(),
                    found.evidence().externalIdFingerprint());
        }
        return outcome;
    }

    private void validateSuccessIdentity(PlatformOperationType operationType, Optional<String> durableEntityExternalId,
            Optional<String> returnedExternalId, Optional<String> fingerprint) {
        if (operationType.name().startsWith("CREATE_")) {
            if (durableEntityExternalId.isPresent() || returnedExternalId.isEmpty()
                    || !fingerprint.equals(Optional.of(sha256(returnedExternalId.orElseThrow())))) {
                throw invalid();
            }
        } else if (durableEntityExternalId.isEmpty() || returnedExternalId.isPresent() || fingerprint.isPresent()) {
            throw invalid();
        }
    }

    private static void require(Object... values) {
        for (Object value : values) if (value == null) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
