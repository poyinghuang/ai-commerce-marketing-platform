package com.aicommerce.platform.quality.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicommerce.platform.product.web.InvalidMergePatchException;
import com.aicommerce.platform.quality.application.QualityValidationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QualityMergePatchParserTest {
    private final QualityMergePatchParser parser = new QualityMergePatchParser();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void distinguishesAbsentNullAndPresentFields() throws Exception {
        var empty = parser.parse(json.readTree("{}"));
        assertThat(empty.manualAdjustment().present()).isFalse();
        assertThat(empty.reason().present()).isFalse();

        var value = parser.parse(json.readTree("{\"manualAdjustment\":-20,\"reason\":null}"));
        assertThat(value.manualAdjustment().value()).isEqualTo(-20);
        assertThat(value.reason().present()).isTrue();
        assertThat(value.reason().value()).isNull();
    }

    @Test
    void rejectsUnknownFieldsTypesAndOutOfRangeValues() throws Exception {
        assertThatThrownBy(() -> parser.parse(json.readTree("{\"version\":1}")))
                .isInstanceOf(InvalidMergePatchException.class);
        assertThatThrownBy(() -> parser.parse(json.readTree("{\"manualAdjustment\":1.5}")))
                .isInstanceOf(QualityValidationException.class);
        assertThatThrownBy(() -> parser.parse(json.readTree("{\"manualAdjustment\":21}")))
                .isInstanceOf(QualityValidationException.class);
        assertThatThrownBy(() -> parser.parse(json.readTree("[]")))
                .isInstanceOf(InvalidMergePatchException.class);
    }
}
