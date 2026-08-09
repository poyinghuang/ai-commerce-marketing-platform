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
    @Column(name = "text_content", updatable = false, length = 16000)
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
    @Column(name = "source_asset_uuid", updatable = false)
    private UUID sourceAssetUuid;
    @Column(name = "mask_asset_uuid", updatable = false)
    private UUID maskAssetUuid;
    @Column(name = "generated_asset_uuid", updatable = false)
    private UUID generatedAssetUuid;
    @Column(name = "generation_mode", updatable = false, length = 32)
    private String generationMode;
    @Column(name = "workflow_key", updatable = false, length = 128)
    private String workflowKey;
    @Column(name = "workflow_version", updatable = false, length = 64)
    private String workflowVersion;
    @Column(name = "image_width", updatable = false)
    private Integer imageWidth;
    @Column(name = "image_height", updatable = false)
    private Integer imageHeight;
    @Column(name = "media_type", updatable = false, length = 64)
    private String mediaType;
    @Column(name = "size_bytes", updatable = false)
    private Long sizeBytes;
    @Column(name = "source_checksum_sha256", updatable = false, length = 64)
    private String sourceChecksumSha256;
    @Column(name = "mask_checksum_sha256", updatable = false, length = 64)
    private String maskChecksumSha256;
    @Column(name = "output_checksum_sha256", updatable = false, length = 64)
    private String outputChecksumSha256;
    @Column(name = "protected_pixels_sha256", updatable = false, length = 64)
    private String protectedPixelsSha256;
    @Column(name = "preservation_algorithm", updatable = false, length = 64)
    private String preservationAlgorithm;
    @Column(name = "preservation_status", updatable = false, length = 16)
    private String preservationStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preservation_details", updatable = false, columnDefinition = "jsonb")
    private String preservationDetails;
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

    public static GenerationOutput createImage(UUID outputUuid, UUID jobUuid, UUID batchUuid, UUID productUuid,
            UUID sourceAssetUuid, UUID maskAssetUuid, UUID generatedAssetUuid,
            String workflowKey, String workflowVersion, int width, int height,
            String mediaType, long sizeBytes, String sourceChecksum, String maskChecksum,
            String outputChecksum, String protectedPixelsChecksum, String preservationStatus,
            String preservationDetails, String modelLabel, BigDecimal actualCost, String currency,
            String safetyFindings, String providerMetadata) {
        GenerationOutput value = new GenerationOutput(outputUuid, jobUuid, batchUuid, productUuid,
                "image-placeholder", modelLabel, 0, 0, actualCost, currency, safetyFindings, providerMetadata);
        value.generationType = GenerationType.IMAGE;
        value.textContent = null;
        value.sourceAssetUuid = Objects.requireNonNull(sourceAssetUuid, "sourceAssetUuid is required");
        value.maskAssetUuid = maskAssetUuid;
        value.generatedAssetUuid = Objects.requireNonNull(generatedAssetUuid, "generatedAssetUuid is required");
        value.generationMode = "BACKGROUND_COMPOSITE";
        value.workflowKey = AiDomainRules.required(workflowKey, "workflowKey", 128);
        value.workflowVersion = AiDomainRules.required(workflowVersion, "workflowVersion", 64);
        if (width < 1 || width > 4096 || height < 1 || height > 4096
                || (long) width * height > 16_777_216L) {
            throw new IllegalArgumentException("image dimensions are invalid");
        }
        value.imageWidth = width;
        value.imageHeight = height;
        if (!"image/png".equals(mediaType) && !"image/jpeg".equals(mediaType)) {
            throw new IllegalArgumentException("mediaType is invalid");
        }
        value.mediaType = mediaType;
        if (sizeBytes < 1 || sizeBytes > 16_777_216L) throw new IllegalArgumentException("sizeBytes is invalid");
        value.sizeBytes = sizeBytes;
        value.sourceChecksumSha256 = checksum(sourceChecksum, "sourceChecksum");
        value.maskChecksumSha256 = maskAssetUuid == null ? null : checksum(maskChecksum, "maskChecksum");
        if ((maskAssetUuid == null) != (maskChecksum == null)) throw new IllegalArgumentException("mask evidence is incoherent");
        value.outputChecksumSha256 = checksum(outputChecksum, "outputChecksum");
        value.protectedPixelsSha256 = checksum(protectedPixelsChecksum, "protectedPixelsChecksum");
        value.preservationAlgorithm = "RGBA_MASK_EXACT_V1";
        if (!"PASSED".equals(preservationStatus) && !"BLOCKED".equals(preservationStatus)) {
            throw new IllegalArgumentException("preservationStatus is invalid");
        }
        value.preservationStatus = preservationStatus;
        value.preservationDetails = AiDomainRules.required(preservationDetails, "preservationDetails", 8192);
        return value;
    }

    private static String checksum(String value, String field) {
        String normalized = AiDomainRules.required(value, field, 64);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
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
    public UUID getSourceAssetUuid() { return sourceAssetUuid; }
    public UUID getMaskAssetUuid() { return maskAssetUuid; }
    public UUID getGeneratedAssetUuid() { return generatedAssetUuid; }
    public String getGenerationMode() { return generationMode; }
    public String getWorkflowKey() { return workflowKey; }
    public String getWorkflowVersion() { return workflowVersion; }
    public Integer getImageWidth() { return imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public String getMediaType() { return mediaType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getSourceChecksumSha256() { return sourceChecksumSha256; }
    public String getMaskChecksumSha256() { return maskChecksumSha256; }
    public String getOutputChecksumSha256() { return outputChecksumSha256; }
    public String getProtectedPixelsSha256() { return protectedPixelsSha256; }
    public String getPreservationAlgorithm() { return preservationAlgorithm; }
    public String getPreservationStatus() { return preservationStatus; }
    public String getPreservationDetails() { return preservationDetails; }
}
