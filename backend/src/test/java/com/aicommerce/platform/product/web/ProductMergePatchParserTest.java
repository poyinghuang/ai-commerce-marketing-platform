package com.aicommerce.platform.product.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicommerce.platform.product.application.ProductValidationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProductMergePatchParserTest {

    private final ProductMergePatchParser parser = new ProductMergePatchParser(new ProductRequestMapper());
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void preservesAbsentAndExplicitNullFields() throws Exception {
        var command = parser.parse(jsonMapper.readTree("{\"brand\":null,\"productName\":\"Updated\"}"));

        assertThat(command.brand().present()).isTrue();
        assertThat(command.brand().value()).isNull();
        assertThat(command.productName().present()).isTrue();
        assertThat(command.productName().value()).isEqualTo("Updated");
        assertThat(command.sku().present()).isFalse();
    }

    @Test
    void rejectsUnknownImmutableAndWrongTypeFields() throws Exception {
        assertThatThrownBy(() -> parser.parse(jsonMapper.readTree("{\"productId\":\"PROD-1\"}")))
                .isInstanceOf(InvalidMergePatchException.class)
                .hasMessageContaining("productId");
        assertThatThrownBy(() -> parser.parse(jsonMapper.readTree("{\"productUuid\":null}")))
                .isInstanceOf(InvalidMergePatchException.class)
                .hasMessageContaining("productUuid");
        assertThatThrownBy(() -> parser.parse(jsonMapper.readTree("{\"cost\":12.5}")))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("decimal string");
        assertThatThrownBy(() -> parser.parse(jsonMapper.readTree("[]")))
                .isInstanceOf(InvalidMergePatchException.class);
    }

    @Test
    void rejectsNullProductName() throws Exception {
        assertThatThrownBy(() -> parser.parse(jsonMapper.readTree("{\"productName\":null}")))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("cannot be null");
    }
}
