package com.aicommerce.platform.ai.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditActor;
import org.junit.jupiter.api.Test;

class ReviewDecisionDomainTest {

    @Test
    void requiresHumanActorAndCoherentReason() {
        assertThatThrownBy(() -> ReviewDecision.create(UUID.randomUUID(), UUID.randomUUID(),
                ReviewDecisionType.APPROVED, null, AuditActor.system("recovery"), "review-domain", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("human reviewer");
        assertThatThrownBy(() -> ReviewDecision.create(UUID.randomUUID(), UUID.randomUUID(),
                ReviewDecisionType.REJECTED, " ", AuditActor.localAdmin(), "review-domain", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> ReviewDecision.create(UUID.randomUUID(), UUID.randomUUID(),
                ReviewDecisionType.APPROVED, "browser reason", AuditActor.localAdmin(), "review-domain", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not accepted");
    }
}
