package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aicommerce.platform.connector.sheets.application.ProductSheetMapping;
import com.aicommerce.platform.connector.sheets.application.SheetProviderException;
import com.aicommerce.platform.connector.sheets.application.SheetSource;
import com.aicommerce.platform.connector.sheets.application.SheetValuesProvider;
import com.aicommerce.platform.connector.sheets.application.SheetValuesSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class StubSheetValuesProvider implements SheetValuesProvider {

    static final String MIXED_PREFIX = "stub-products-mixed_";
    private static final Pattern MIXED_SOURCE = Pattern.compile("^" + MIXED_PREFIX + "(PROD-[0-9]{8})$");

    @Override
    public SheetValuesSnapshot read(SheetSource source) {
        if ("stub-products".equals(source.spreadsheetId())) {
            return singleCreateSnapshot();
        }

        Matcher mixed = MIXED_SOURCE.matcher(source.spreadsheetId());
        if (mixed.matches()) {
            return mixedAcceptanceSnapshot(mixed.group(1));
        }

        throw new SheetProviderException("CONNECTOR_NOT_CONFIGURED",
                "The local Sheet stub supports only documented deterministic fixture identifiers");
    }

    private SheetValuesSnapshot singleCreateSnapshot() {
        return new SheetValuesSnapshot(List.of(
                ProductSheetMapping.HEADERS,
                List.of("", "", "STUB-001", "Stub Product", "Stub Brand", "Stub Category", "",
                        "Created by the deterministic local connector", "10.00", "12.00", "TWD", "5",
                        "https://example.com/stub-product")));
    }

    private SheetValuesSnapshot mixedAcceptanceSnapshot(String existingProductId) {
        return new SheetValuesSnapshot(List.of(
                ProductSheetMapping.HEADERS,
                List.of("", "", "STUB-CREATE-001", "Stub Created Product", "Stub Brand", "Stub Category",
                        "Stub Subcategory", "Created by the Stage 02 acceptance fixture", "10.00", "12.00",
                        "TWD", "5", "https://example.com/stub-created-product"),
                List.of("", existingProductId, "STUB-UPDATE-001", "Stub Updated Product", "Updated Brand",
                        "Updated Category", "Updated Subcategory", "Updated by the Stage 02 acceptance fixture",
                        "20.00", "30.00", "TWD", "10", "https://example.com/stub-updated-product"),
                List.of("", "", "STUB-INVALID-001", "", "Invalid Brand", "Invalid Category", "", "",
                        "", "", "", "", "")));
    }
}
