package com.aicommerce.platform.delivery.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stage 4B identifiers are derived from the already durable request identity. This makes
 * concurrent winner/loser graphs observable without weakening the idempotency boundary.
 */
@Component
@Profile("(local | test) & !production")
public class Stage4BUuidSource {
    public UUID request(UUID requestUuid, String role) {
        return named("request\n" + requestUuid.toString().toLowerCase() + "\n" + role);
    }

    public UUID accountDay(UUID accountUuid, LocalDate businessDate, String currency) {
        return named("account-day\n" + accountUuid.toString().toLowerCase() + "\n" + businessDate + "\n" + currency);
    }

    private static UUID named(String value) {
        return UUID.nameUUIDFromBytes(("stage4b-uuid-v1\n" + value).getBytes(StandardCharsets.UTF_8));
    }
}
