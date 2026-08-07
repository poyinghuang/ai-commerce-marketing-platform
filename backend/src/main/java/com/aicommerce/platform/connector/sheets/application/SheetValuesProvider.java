package com.aicommerce.platform.connector.sheets.application;

public interface SheetValuesProvider {
    SheetValuesSnapshot read(SheetSource source);
}
