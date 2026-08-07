package com.aicommerce.platform.common.application;

public record FieldPatch<T>(boolean present, T value) {

    public static <T> FieldPatch<T> absent() {
        return new FieldPatch<>(false, null);
    }

    public static <T> FieldPatch<T> present(T value) {
        return new FieldPatch<>(true, value);
    }

    public T resolve(T currentValue) {
        return present ? value : currentValue;
    }
}
