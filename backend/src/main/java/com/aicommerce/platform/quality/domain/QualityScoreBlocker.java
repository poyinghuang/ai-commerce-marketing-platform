package com.aicommerce.platform.quality.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_score_blockers")
public class QualityScoreBlocker {
    @Id
    @Column(name = "quality_score_blocker_uuid", nullable = false, updatable = false)
    private UUID qualityScoreBlockerUuid;
    @Column(name = "quality_score_uuid", nullable = false, updatable = false)
    private UUID qualityScoreUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "blocker_code", nullable = false, length = 64)
    private QualityBlockerCode blockerCode;
    @Column(name = "field_path", length = 256)
    private String fieldPath;
    @Column(name = "message", nullable = false, length = 512)
    private String message;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QualityScoreBlocker() {}

    public static QualityScoreBlocker create(UUID uuid, UUID qualityScoreUuid,
            QualityBlockerCode code, Instant createdAt) {
        var blocker = new QualityScoreBlocker();
        blocker.qualityScoreBlockerUuid = Objects.requireNonNull(uuid, "uuid is required");
        blocker.qualityScoreUuid = Objects.requireNonNull(qualityScoreUuid, "qualityScoreUuid is required");
        blocker.blockerCode = Objects.requireNonNull(code, "code is required");
        blocker.fieldPath = code.fieldPath();
        blocker.message = code.message();
        blocker.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        return blocker;
    }

    public UUID getQualityScoreBlockerUuid() { return qualityScoreBlockerUuid; }
    public UUID getQualityScoreUuid() { return qualityScoreUuid; }
    public QualityBlockerCode getBlockerCode() { return blockerCode; }
    public String getFieldPath() { return fieldPath; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
