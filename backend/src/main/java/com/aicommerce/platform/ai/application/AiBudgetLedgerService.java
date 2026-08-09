package com.aicommerce.platform.ai.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.domain.BudgetEntryType;
import com.aicommerce.platform.ai.domain.BudgetLedgerEntry;
import com.aicommerce.platform.ai.infrastructure.persistence.BudgetLedgerJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationBatchJpaRepository;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationJobJpaRepository;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiBudgetLedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6);
    private static final String ENTITY_TYPE = "AI_BUDGET_LEDGER";

    private final AiBudgetPolicyProvider policyProvider;
    private final BudgetLedgerJpaRepository ledgerRepository;
    private final GenerationJobJpaRepository jobRepository;
    private final GenerationBatchJpaRepository batchRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public AiBudgetLedgerService(AiBudgetPolicyProvider policyProvider,
            BudgetLedgerJpaRepository ledgerRepository, GenerationJobJpaRepository jobRepository,
            GenerationBatchJpaRepository batchRepository, JdbcTemplate jdbcTemplate,
            AuditWriter auditWriter, Clock clock) {
        this.policyProvider = policyProvider;
        this.ledgerRepository = ledgerRepository;
        this.jobRepository = jobRepository;
        this.batchRepository = batchRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = AiBudgetExceededException.class)
    public void reserve(List<BudgetReservation> reservations, String currency, UUID productUuid,
            AuditOperationContext context) {
        if (reservations == null || reservations.isEmpty()) {
            throw new IllegalArgumentException("At least one budget reservation is required");
        }
        AiBudgetPolicy policy = policyProvider.currentPolicy();
        if (!policy.currency().equals(currency)) {
            throw new AiBudgetExceededException("currency");
        }
        BigDecimal batchTotal = ZERO;
        for (BudgetReservation reservation : reservations) {
            BigDecimal amount = canonical(reservation.worstCaseCost());
            if (amount.compareTo(policy.maximumJobCost()) > 0) {
                throw new AiBudgetExceededException("job");
            }
            batchTotal = batchTotal.add(amount);
        }
        if (batchTotal.compareTo(policy.maximumBatchCost()) > 0) {
            throw new AiBudgetExceededException("batch");
        }

        LocalDate budgetDate = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        lockDay(budgetDate, currency);
        BigDecimal currentUsage = currentUsage(budgetDate, currency);
        if (currentUsage.add(batchTotal).compareTo(policy.maximumDailyCost()) > 0) {
            throw new AiBudgetExceededException("daily");
        }

        for (BudgetReservation reservation : reservations) {
            appendEntry(reservation.generationJobUuid(), budgetDate, BudgetEntryType.RESERVE,
                    canonical(reservation.worstCaseCost()), currency, 0, productUuid, context);
        }
        ledgerRepository.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BudgetSettlementResult settle(UUID jobUuid, BigDecimal actualCost, UUID productUuid,
            AuditOperationContext context) {
        BigDecimal actual = canonicalNonNegative(actualCost, "actualCost");
        LedgerState state = lockAndRead(jobUuid);
        if (state.committed() != null || state.released() != null) {
            throw new IllegalStateException("AI budget reservation is already settled");
        }
        int order = 1;
        if (actual.signum() > 0) {
            appendEntry(jobUuid, state.budgetDate(), BudgetEntryType.COMMIT, actual, state.currency(),
                    order++, productUuid, context);
        }
        BigDecimal released = state.reserved().subtract(actual).max(ZERO);
        if (released.signum() > 0) {
            appendEntry(jobUuid, state.budgetDate(), BudgetEntryType.RELEASE, released, state.currency(),
                    order, productUuid, context);
        }
        var job = jobRepository.findById(jobUuid)
                .orElseThrow(() -> new IllegalStateException("AI generation job does not exist"));
        var batch = batchRepository.findById(job.getGenerationBatchUuid())
                .orElseThrow(() -> new IllegalStateException("AI generation batch does not exist"));
        BigDecimal previousJobActual = job.getActualCost();
        BigDecimal previousBatchActual = batch.getActualCost();
        if (job.recordActualCost(actual)) {
            BigDecimal batchActual = previousBatchActual.add(actual.subtract(previousJobActual));
            batch.recordActualCost(batchActual);
            jobRepository.save(job);
            batchRepository.save(batch);
            auditWriter.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.UPDATE,
                    "AI_GENERATION_JOB", jobUuid, productUuid, Instant.now(clock), List.of(
                            new AuditChange("actualCost", previousJobActual.toPlainString(),
                                    actual.toPlainString(), AuditValueType.DECIMAL, 0))));
            auditWriter.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.UPDATE,
                    "AI_GENERATION_BATCH", batch.getGenerationBatchUuid(), productUuid,
                    Instant.now(clock), List.of(
                            new AuditChange("actualCost", previousBatchActual.toPlainString(),
                                    batchActual.toPlainString(), AuditValueType.DECIMAL, 0))));
        }
        ledgerRepository.flush();
        jobRepository.flush();
        batchRepository.flush();
        return new BudgetSettlementResult(state.reserved(), actual, released,
                actual.compareTo(state.reserved()) > 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(UUID jobUuid, UUID productUuid, AuditOperationContext context) {
        LedgerState state = lockAndRead(jobUuid);
        if (state.committed() != null || state.released() != null) {
            return false;
        }
        appendEntry(jobUuid, state.budgetDate(), BudgetEntryType.RELEASE, state.reserved(),
                state.currency(), 1, productUuid, context);
        ledgerRepository.flush();
        return true;
    }

    private LedgerState lockAndRead(UUID jobUuid) {
        Integer locked = jdbcTemplate.queryForObject(
                "SELECT 1 FROM ai_generation_jobs WHERE generation_job_uuid = ? FOR UPDATE",
                Integer.class, jobUuid);
        if (locked == null) {
            throw new IllegalStateException("AI generation job does not exist");
        }
        List<LedgerState> states = jdbcTemplate.query("""
                SELECT budget_date, currency,
                       MAX(amount) FILTER (WHERE entry_type='RESERVE') AS reserved,
                       MAX(amount) FILTER (WHERE entry_type='COMMIT') AS committed,
                       MAX(amount) FILTER (WHERE entry_type='RELEASE') AS released
                  FROM ai_budget_ledger
                 WHERE generation_job_uuid = ?
                 GROUP BY budget_date, currency
                """, (resultSet, row) -> new LedgerState(
                        resultSet.getObject("budget_date", LocalDate.class),
                        resultSet.getString("currency").trim(),
                        resultSet.getBigDecimal("reserved"),
                        resultSet.getBigDecimal("committed"),
                        resultSet.getBigDecimal("released")), jobUuid);
        if (states.size() != 1 || states.getFirst().reserved() == null) {
            throw new IllegalStateException("AI budget reservation does not exist");
        }
        return states.getFirst();
    }

    private void lockDay(LocalDate date, String currency) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                resultSet -> null, date + ":" + currency);
    }

    private BigDecimal currentUsage(LocalDate date, String currency) {
        BigDecimal value = jdbcTemplate.queryForObject("""
                WITH per_job AS (
                    SELECT generation_job_uuid,
                           MAX(amount) FILTER (WHERE entry_type='RESERVE') AS reserved,
                           MAX(amount) FILTER (WHERE entry_type='COMMIT') AS committed,
                           COALESCE(MAX(amount) FILTER (WHERE entry_type='RELEASE'), 0) AS released
                      FROM ai_budget_ledger
                     WHERE budget_date = ? AND currency = ?
                     GROUP BY generation_job_uuid
                )
                SELECT COALESCE(SUM(
                    CASE WHEN committed IS NOT NULL THEN committed ELSE reserved - released END
                ), 0) FROM per_job
                """, BigDecimal.class, date, currency);
        return value == null ? ZERO : value;
    }

    private void appendEntry(UUID jobUuid, LocalDate date, BudgetEntryType type, BigDecimal amount,
            String currency, int order, UUID productUuid, AuditOperationContext context) {
        BudgetLedgerEntry entry = BudgetLedgerEntry.create(UUID.randomUUID(), jobUuid, date, type,
                amount, currency, order);
        ledgerRepository.save(entry);
        List<AuditChange> changes = new ArrayList<>();
        changes.add(new AuditChange("entryType", null, type.name(), AuditValueType.ENUM, 0));
        changes.add(new AuditChange("amount", null, amount.toPlainString(), AuditValueType.DECIMAL, 1));
        changes.add(new AuditChange("budgetDate", null, date.toString(), AuditValueType.DATE, 2));
        auditWriter.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.CREATE, ENTITY_TYPE,
                entry.getBudgetLedgerUuid(), productUuid, Instant.now(clock), changes));
    }

    private BigDecimal canonical(BigDecimal value) {
        return canonicalNonNegative(value, "budget amount");
    }

    private BigDecimal canonicalNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 6
                || value.precision() - value.scale() > 13) {
            throw new IllegalArgumentException(field + " must be a non-negative numeric(19,6) value");
        }
        return value.setScale(6);
    }

    private record LedgerState(LocalDate budgetDate, String currency, BigDecimal reserved,
            BigDecimal committed, BigDecimal released) {
    }
}
