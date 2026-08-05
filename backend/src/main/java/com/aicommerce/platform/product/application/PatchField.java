package com.aicommerce.platform.product.application;

public record PatchField<T>(boolean present, T value) {

    public static <T> PatchField<T> absent() {
        return new PatchField<>(false, null);
    }

    public static <T> PatchField<T> present(T value) {
        return new PatchField<>(true, value);
    }

    public T resolve(T currentValue) {
        return present ? value : currentValue;
    }
}
