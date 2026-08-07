package com.aicommerce.platform.asset.web;

import static org.assertj.core.api.Assertions.*;
import com.aicommerce.platform.asset.application.AssetValidationException;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AssetMergePatchParserTest {
    private final JsonMapper json=new JsonMapper(); private final AssetMergePatchParser parser=new AssetMergePatchParser(json);
    @Test void distinguishesAbsentNullValueAndMetadataObject() {
        var c=parser.parse(json.readTree("{\"purpose\":null,\"sizeBytes\":0,\"providerMetadata\":{\"region\":\"eu\"}}"));
        assertThat(c.assetType().present()).isFalse(); assertThat(c.purpose().present()).isTrue(); assertThat(c.purpose().value()).isNull();
        assertThat(c.sizeBytes().value()).isZero(); assertThat(c.providerMetadata().value()).containsEntry("region","eu");
    }
    @Test void rejectsRootArrayImmutableFieldsNullTypeAndMetadataScalar() {
        assertThatThrownBy(()->parser.parse(json.readTree("[]"))).isInstanceOf(InvalidMergePatchException.class);
        assertThatThrownBy(()->parser.parse(json.readTree("{\"productUuid\":null}"))).isInstanceOf(InvalidMergePatchException.class);
        assertThatThrownBy(()->parser.parse(json.readTree("{\"assetType\":null}"))).isInstanceOf(AssetValidationException.class);
        assertThatThrownBy(()->parser.parse(json.readTree("{\"providerMetadata\":[]}"))).isInstanceOf(AssetValidationException.class);
    }
}
