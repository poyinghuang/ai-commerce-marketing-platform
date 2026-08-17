package com.aicommerce.platform.delivery.domain;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(name="platform_operation_attempts") public class PlatformOperationAttempt {
 @Id @Column(name="operation_attempt_uuid",updatable=false) private UUID operationAttemptUuid;
 @Column(name="operation_uuid",updatable=false,nullable=false) private UUID operationUuid;
 @Enumerated(EnumType.STRING) @Column(name="attempt_kind",updatable=false,nullable=false) private PlatformAttemptKind attemptKind;
 @Column(name="attempt_number",updatable=false,nullable=false) private int attemptNumber;
 @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private PlatformAttemptStatus status;
 @Column(name="safe_provider_trace_id") private String safeProviderTraceId; @Column(name="normalized_error_code") private String normalizedErrorCode;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="evidence",columnDefinition="jsonb") private String evidence;
 @Column(name="started_at",updatable=false,nullable=false) private Instant startedAt; @Column(name="completed_at") private Instant completedAt;
 @Column(name="created_at",updatable=false,insertable=false) private Instant createdAt; @Version @Column(name="version",nullable=false) private long version;
 protected PlatformOperationAttempt(){}
}
