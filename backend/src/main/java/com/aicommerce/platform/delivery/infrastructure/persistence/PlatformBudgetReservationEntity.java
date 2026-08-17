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
@Table(name="platform_budget_reservations")
public class PlatformBudgetReservationEntity {
    @Id @Column(name="budget_reservation_uuid",nullable=false) private UUID id;
    @Column(name="operation_batch_uuid",nullable=false,unique=true) private UUID operationBatchUuid;
    @Column(name="operation_uuid",nullable=false,unique=true) private UUID operationUuid;
    @Column(name="platform_account_uuid",nullable=false) private UUID platformAccountUuid;
    @Column(name="account_budget_day_uuid",nullable=false) private UUID accountBudgetDayUuid;
    @Column(name="platform_ad_set_uuid",nullable=false) private UUID platformAdSetUuid;
    @Column(name="reservation_kind",nullable=false,length=32) private String reservationKind;
    @Column(name="previous_budget_amount",precision=19,scale=6) private BigDecimal previousBudgetAmount;
    @Column(name="new_budget_amount",nullable=false,precision=19,scale=6) private BigDecimal newBudgetAmount;
    @Column(name="reserved_amount",nullable=false,precision=19,scale=6) private BigDecimal reservedAmount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,columnDefinition="char(3)") private String currency;
    @Column(name="business_date",nullable=false) private LocalDate businessDate;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected PlatformBudgetReservationEntity() {}
}
