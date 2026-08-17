package com.aicommerce.platform.delivery.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "platform_operation_batches")
public class PlatformOperationBatchEntity {
    @Id @Column(name="operation_batch_uuid",nullable=false) private UUID id;
    @Column(name="operation_uuid",nullable=false,unique=true) private UUID operationUuid;
    @Column(name="platform_account_uuid",nullable=false) private UUID platformAccountUuid;
    @Column(name="client_request_uuid",nullable=false) private UUID clientRequestUuid;
    @Column(name="requested_actor_type",nullable=false,length=32) private String requestedActorType;
    @Column(name="requested_actor_id",nullable=false,length=128) private String requestedActorId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,columnDefinition="char(3)") private String currency;
    @Column(name="business_date",nullable=false) private LocalDate businessDate;
    @Column(name="reserved_amount",nullable=false,precision=19,scale=6) private BigDecimal reservedAmount;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(nullable=false) private long version;
    protected PlatformOperationBatchEntity() {}
}
