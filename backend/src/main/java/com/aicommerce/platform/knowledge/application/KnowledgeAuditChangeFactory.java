package com.aicommerce.platform.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeAuditChangeFactory {
    public List<AuditChange> create(KnowledgeSnapshot after) { return differences(null, after); }
    public List<AuditChange> between(KnowledgeSnapshot before, KnowledgeSnapshot after) { return differences(before, after); }
    private List<AuditChange> differences(KnowledgeSnapshot before, KnowledgeSnapshot after) {
        List<AuditChange> result = new ArrayList<>();
        add(result, "knowledge_type", before == null ? null : before.knowledgeType().name(), after.knowledgeType().name(), AuditValueType.ENUM);
        add(result, "title", before == null ? null : before.title(), after.title(), AuditValueType.STRING);
        add(result, "content", before == null ? null : before.content(), after.content(), AuditValueType.STRING);
        add(result, "source", before == null ? null : before.source(), after.source(), AuditValueType.STRING);
        add(result, "lifecycle_status", before == null ? null : before.lifecycleStatus().name(), after.lifecycleStatus().name(), AuditValueType.ENUM);
        add(result, "archived_at", before == null || before.archivedAt() == null ? null : before.archivedAt().toString(),
                after.archivedAt() == null ? null : after.archivedAt().toString(), AuditValueType.TIMESTAMP);
        return List.copyOf(result);
    }
    private void add(List<AuditChange> result, String field, String oldValue, String newValue, AuditValueType type) {
        if (!Objects.equals(oldValue, newValue)) result.add(new AuditChange(field, oldValue, newValue, type, result.size()));
    }
}
