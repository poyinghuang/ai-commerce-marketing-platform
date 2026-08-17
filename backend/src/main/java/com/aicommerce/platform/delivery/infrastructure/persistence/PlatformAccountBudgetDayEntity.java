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
@Table(name="platform_account_budget_days")
public class PlatformAccountBudgetDayEntity {
    @Id @Column(name="account_budget_day_uuid",nullable=false,unique=true) private UUID id;
    @Column(name="platform_account_uuid",nullable=false) private UUID platformAccountUuid;
    @Column(name="business_date",nullable=false) private LocalDate businessDate;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,columnDefinition="char(3)") private String currency;
    @Column(name="reserved_amount",nullable=false,precision=19,scale=6) private BigDecimal reservedAmount;
    @Column(name="ceiling_amount",nullable=false,precision=19,scale=6) private BigDecimal ceilingAmount;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(nullable=false) private long version;
    protected PlatformAccountBudgetDayEntity() {}
}
