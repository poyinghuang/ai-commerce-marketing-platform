package com.aicommerce.platform.knowledge.application;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import org.junit.jupiter.api.Test;
class KnowledgeAuditChangeFactoryTest {
 private final KnowledgeAuditChangeFactory factory=new KnowledgeAuditChangeFactory();
 @Test void emitsOnlyActualChangesInDeterministicOrder(){UUID k=UUID.randomUUID(),p=UUID.randomUUID();var before=new KnowledgeSnapshot(k,p,KnowledgeType.FEATURE,"Title","Content",null,LifecycleStatus.ACTIVE,null);var after=new KnowledgeSnapshot(k,p,KnowledgeType.FEATURE,"Updated","Content",null,LifecycleStatus.ARCHIVED,Instant.parse("2026-08-07T00:00:00Z"));var changes=factory.between(before,after);assertThat(changes).extracting(c->c.fieldName()).containsExactly("title","lifecycle_status","archived_at");assertThat(changes).extracting(c->c.changeOrder()).containsExactly(0,1,2);}
 @Test void noChangeProducesNoAuditChanges(){UUID k=UUID.randomUUID(),p=UUID.randomUUID();var value=new KnowledgeSnapshot(k,p,KnowledgeType.FAQ,"Question","Answer",null,LifecycleStatus.ACTIVE,null);assertThat(factory.between(value,value)).isEmpty();}
}
