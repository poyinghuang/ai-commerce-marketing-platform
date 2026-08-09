package com.aicommerce.platform.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "ai_budget_ledger")
@EntityListeners(AuditingEntityListener.class)
public class BudgetLedgerEntry {
    @Id @Column(name = "budget_ledger_uuid", nullable = false, updatable = false)
    private UUID budgetLedgerUuid;
    @Column(name = "generation_job_uuid", nullable = false, updatable = false)
    private UUID generationJobUuid;
    @Column(name = "budget_date", nullable = false, updatable = false)
    private LocalDate budgetDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 16)
    private BudgetEntryType entryType;
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;
    @Column(name = "entry_order", nullable = false, updatable = false)
    private int entryOrder;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BudgetLedgerEntry() {
    }

    private BudgetLedgerEntry(UUID id, UUID jobId, LocalDate budgetDate, BudgetEntryType entryType,
            BigDecimal amount, String currency, int entryOrder) {
        this.budgetLedgerUuid = Objects.requireNonNull(id, "budgetLedgerUuid is required");
        this.generationJobUuid = Objects.requireNonNull(jobId, "generationJobUuid is required");
        this.budgetDate = Objects.requireNonNull(budgetDate, "budgetDate is required");
        this.entryType = Objects.requireNonNull(entryType, "entryType is required");
        this.amount = AiDomainRules.money(amount, "amount", true);
        this.currency = AiDomainRules.currency(currency);
        if (entryOrder < 0) throw new IllegalArgumentException("entryOrder must be non-negative");
        this.entryOrder = entryOrder;
    }

    public static BudgetLedgerEntry create(UUID id, UUID jobId, LocalDate budgetDate,
            BudgetEntryType entryType, BigDecimal amount, String currency, int entryOrder) {
        return new BudgetLedgerEntry(id, jobId, budgetDate, entryType, amount, currency, entryOrder);
    }

    public UUID getBudgetLedgerUuid() { return budgetLedgerUuid; }
    public UUID getGenerationJobUuid() { return generationJobUuid; }
    public LocalDate getBudgetDate() { return budgetDate; }
    public BudgetEntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public int getEntryOrder() { return entryOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
