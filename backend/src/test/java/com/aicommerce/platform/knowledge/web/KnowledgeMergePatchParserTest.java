package com.aicommerce.platform.knowledge.web;
import static org.assertj.core.api.Assertions.*;
import com.aicommerce.platform.knowledge.application.KnowledgeValidationException;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
class KnowledgeMergePatchParserTest {
 private final KnowledgeMergePatchParser parser=new KnowledgeMergePatchParser(); private final JsonMapper json=JsonMapper.builder().build();
 @Test void distinguishesAbsentExplicitNullAndValues() throws Exception {var c=parser.parse(json.readTree("{\"knowledgeType\":\"faq\",\"source\":null}"));assertThat(c.knowledgeType().present()).isTrue();assertThat(c.knowledgeType().value()).isEqualTo(KnowledgeType.FAQ);assertThat(c.source().present()).isTrue();assertThat(c.source().value()).isNull();assertThat(c.title().present()).isFalse();}
 @Test void rejectsUnknownImmutableNullRequiredAndWrongTypes() throws Exception {assertThatThrownBy(()->parser.parse(json.readTree("{\"productUuid\":null}"))).isInstanceOf(InvalidMergePatchException.class);assertThatThrownBy(()->parser.parse(json.readTree("{\"title\":null}"))).isInstanceOf(KnowledgeValidationException.class);assertThatThrownBy(()->parser.parse(json.readTree("{\"content\":42}"))).isInstanceOf(KnowledgeValidationException.class);assertThatThrownBy(()->parser.parse(json.readTree("[]"))).isInstanceOf(InvalidMergePatchException.class);}
}
