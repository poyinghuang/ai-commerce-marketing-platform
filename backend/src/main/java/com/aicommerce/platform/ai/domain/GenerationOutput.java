package com.aicommerce.platform.ai.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_generation_outputs")
public class GenerationOutput extends MutableEntity {

    @Id
    @Column(name = "generation_output_uuid", nullable = false, updatable = false)
    private UUID generationOutputUuid;
    @Column(name = "generation_job_uuid", nullable = false, updatable = false)
    private UUID generationJobUuid;
    @Column(name = "generation_batch_uuid", nullable = false, updatable = false)
    private UUID generationBatchUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", nullable = false, updatable = false, length = 16)
    private GenerationType generationType;
    @Column(name = "text_content", nullable = false, updatable = false, length = 16000)
    private String textContent;
    @Column(name = "model_label", nullable = false, updatable = false, length = 128)
    private String modelLabel;
    @Column(name = "input_units", nullable = false, updatable = false)
    private long inputUnits;
    @Column(name = "output_units", nullable = false, updatable = false)
    private long outputUnits;
    @Column(name = "actual_cost", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal actualCost;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_findings", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String safetyFindings;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String providerMetadata;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 24)
    private GenerationOutputReviewStatus reviewStatus;

    protected GenerationOutput() {
    }

    private GenerationOutput(UUID outputUuid, UUID jobUuid, UUID batchUuid, UUID productUuid,
            String textContent, String modelLabel, long inputUnits, long outputUnits,
            BigDecimal actualCost, String currency, String safetyFindings, String providerMetadata) {
        this.generationOutputUuid = Objects.requireNonNull(outputUuid, "generationOutputUuid is required");
        this.generationJobUuid = Objects.requireNonNull(jobUuid, "generationJobUuid is required");
        this.generationBatchUuid = Objects.requireNonNull(batchUuid, "generationBatchUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.generationType = GenerationType.TEXT;
        this.textContent = AiDomainRules.required(textContent, "textContent", 16000);
        this.modelLabel = AiDomainRules.required(modelLabel, "modelLabel", 128);
        if (inputUnits < 0 || outputUnits < 0) throw new IllegalArgumentException("usage must be non-negative");
        this.inputUnits = inputUnits;
        this.outputUnits = outputUnits;
        this.actualCost = AiDomainRules.money(actualCost, "actualCost", false);
        this.currency = AiDomainRules.currency(currency);
        this.safetyFindings = AiDomainRules.required(safetyFindings, "safetyFindings", 8192);
        this.providerMetadata = AiDomainRules.required(providerMetadata, "providerMetadata", 8192);
        this.reviewStatus = GenerationOutputReviewStatus.PENDING_REVIEW;
    }

    public static GenerationOutput createText(UUID outputUuid, UUID jobUuid, UUID batchUuid, UUID productUuid,
            String textContent, String modelLabel, long inputUnits, long outputUnits,
            BigDecimal actualCost, String currency, String safetyFindings, String providerMetadata) {
        return new GenerationOutput(outputUuid, jobUuid, batchUuid, productUuid, textContent, modelLabel,
                inputUnits, outputUnits, actualCost, currency, safetyFindings, providerMetadata);
    }

    public UUID getGenerationOutputUuid() { return generationOutputUuid; }
    public UUID getGenerationJobUuid() { return generationJobUuid; }
    public UUID getGenerationBatchUuid() { return generationBatchUuid; }
    public UUID getProductUuid() { return productUuid; }
    public GenerationType getGenerationType() { return generationType; }
    public String getTextContent() { return textContent; }
    public String getModelLabel() { return modelLabel; }
    public long getInputUnits() { return inputUnits; }
    public long getOutputUnits() { return outputUnits; }
    public BigDecimal getActualCost() { return actualCost; }
    public String getCurrency() { return currency; }
    public String getSafetyFindings() { return safetyFindings; }
    public String getProviderMetadata() { return providerMetadata; }
    public GenerationOutputReviewStatus getReviewStatus() { return reviewStatus; }
}
