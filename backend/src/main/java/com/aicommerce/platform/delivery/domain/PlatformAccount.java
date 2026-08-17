package com.aicommerce.platform.delivery.domain;
import java.time.Instant; import java.util.UUID; import com.aicommerce.platform.common.persistence.MutableEntity; import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(name="platform_accounts") public class PlatformAccount extends MutableEntity {
 @Id @Column(name="platform_account_uuid",updatable=false) private UUID platformAccountUuid;
 @Enumerated(EnumType.STRING) @Column(name="provider_key",updatable=false) private ProviderKey providerKey;
 @Enumerated(EnumType.STRING) @Column(name="environment",updatable=false) private PlatformEnvironment environment;
 @Column(name="account_reference",updatable=false) private String accountReference; @JdbcTypeCode(SqlTypes.CHAR) @Column(name="external_account_fingerprint",updatable=false,columnDefinition="char(64)") private String externalAccountFingerprint;
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="currency",updatable=false,columnDefinition="char(3)") private String currency; @Column(name="timezone",updatable=false) private String timezone;
 @Enumerated(EnumType.STRING) @Column(name="lifecycle_status") private PlatformAccountStatus lifecycleStatus; @Column(name="archived_at") private Instant archivedAt;
 protected PlatformAccount(){} public UUID getPlatformAccountUuid(){return platformAccountUuid;} public ProviderKey getProviderKey(){return providerKey;} public String getCurrency(){return currency;} public String getTimezone(){return timezone;}
}
