package com.aicommerce.platform.connector.sheets.application;

import java.util.List;

public record SheetValuesSnapshot(List<List<String>> values) {

    public SheetValuesSnapshot {
        values = values == null
                ? List.of()
                : values.stream()
                        .map(row -> row == null
                                ? List.<String>of()
                                : row.stream().map(cell -> cell == null ? "" : cell).toList())
                        .toList();
    }
}
