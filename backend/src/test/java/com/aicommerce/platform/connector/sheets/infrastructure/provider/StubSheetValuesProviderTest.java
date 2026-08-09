package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicommerce.platform.connector.sheets.application.ProductSheetMapping;
import com.aicommerce.platform.connector.sheets.application.SheetProviderException;
import com.aicommerce.platform.connector.sheets.application.SheetSource;
import org.junit.jupiter.api.Test;

class StubSheetValuesProviderTest {

    private final StubSheetValuesProvider provider = new StubSheetValuesProvider();

    @Test
    void suppliesTheDocumentedSingleCreateFixture() {
        var snapshot = provider.read(source("stub-products"));

        assertThat(snapshot.values()).hasSize(2);
        assertThat(snapshot.values().getFirst()).isEqualTo(ProductSheetMapping.HEADERS);
        assertThat(snapshot.values().get(1).get(3)).isEqualTo("Stub Product");
    }

    @Test
    void suppliesAConstrainedMixedAcceptanceFixtureForAnExistingProductId() {
        var snapshot = provider.read(source("stub-products-mixed_PROD-00000123"));

        assertThat(snapshot.values()).hasSize(4);
        assertThat(snapshot.values()).extracting(row -> row.get(3))
                .containsExactly("product_name", "Stub Created Product", "Stub Updated Product", "");
        assertThat(snapshot.values().get(2).get(1)).isEqualTo("PROD-00000123");
    }

    @Test
    void rejectsUnknownOrMalformedFixtureIdentifiers() {
        assertThatThrownBy(() -> provider.read(source("stub-products-mixed_PROD-123")))
                .isInstanceOf(SheetProviderException.class)
                .hasMessageContaining("documented deterministic fixture");
        assertThatThrownBy(() -> provider.read(source("untrusted-fixture")))
                .isInstanceOf(SheetProviderException.class);
    }

    private SheetSource source(String spreadsheetId) {
        return new SheetSource(spreadsheetId, "Products", "Products!A1:M1001");
    }
}
