package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import java.util.List;

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

    @Override
    public SheetValuesSnapshot read(SheetSource source) {
        if (!"stub-products".equals(source.spreadsheetId())) {
            throw new SheetProviderException("CONNECTOR_NOT_CONFIGURED",
                    "The local Sheet stub only supports spreadsheetId stub-products");
        }
        return new SheetValuesSnapshot(List.of(
                ProductSheetMapping.HEADERS,
                List.of("", "", "STUB-001", "Stub Product", "Stub Brand", "Stub Category", "",
                        "Created by the deterministic local connector", "10.00", "12.00", "TWD", "5",
                        "https://example.com/stub-product")));
    }
}
