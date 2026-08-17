package com.aicommerce.platform.delivery.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.delivery.application.PlatformOperationException;
import com.aicommerce.platform.delivery.application.port.PlatformAccountPolicy;
import com.aicommerce.platform.delivery.application.port.PlatformAccountPolicyProvider;
import com.aicommerce.platform.delivery.domain.PlatformEnvironment;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class DatabasePlatformAccountPolicyProvider implements PlatformAccountPolicyProvider {
    private final JdbcTemplate jdbc;

    public DatabasePlatformAccountPolicyProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PlatformAccountPolicy requirePolicy(UUID accountUuid) {
        try {
            return jdbc.queryForObject("""
                    SELECT provider_key,environment,currency,timezone,lifecycle_status
                    FROM platform_accounts WHERE platform_account_uuid=?
                    """, (rs, row) -> new PlatformAccountPolicy(accountUuid,
                            ProviderKey.valueOf(rs.getString("provider_key")),
                            PlatformEnvironment.valueOf(rs.getString("environment")), rs.getString("currency"),
                            rs.getString("timezone"), "ACTIVE".equals(rs.getString("lifecycle_status"))), accountUuid);
        } catch (EmptyResultDataAccessException exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_POLICY_REJECTED,
                    java.util.Optional.empty());
        }
    }
}
