package com.aicommerce.platform.creativeplan.web;
import static org.assertj.core.api.Assertions.*;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
class CreativePlanMergePatchParserTest {
 private final CreativePlanMergePatchParser parser=new CreativePlanMergePatchParser(); private final JsonMapper json=new JsonMapper();
 @Test void distinguishesAbsentNullAndValueAcrossWritableFields() throws Exception {var c=parser.parse(json.readTree("{\"primaryAudience\":null,\"cta\":\" Buy \"}"));assertThat(c.planName().present()).isFalse();assertThat(c.primaryAudience().present()).isTrue();assertThat(c.primaryAudience().value()).isNull();assertThat(c.cta().value()).isEqualTo(" Buy ");}
 @Test void rejectsNonObjectAndImmutableFields(){assertThatThrownBy(()->parser.parse(json.readTree("[]"))).isInstanceOf(InvalidMergePatchException.class);assertThatThrownBy(()->parser.parse(json.readTree("{\"productUuid\":null}"))).isInstanceOf(InvalidMergePatchException.class);}
}
