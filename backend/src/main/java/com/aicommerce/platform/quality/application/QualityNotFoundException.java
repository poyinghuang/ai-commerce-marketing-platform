package com.aicommerce.platform.quality.application;

public class QualityNotFoundException extends RuntimeException {
    public QualityNotFoundException() { super("Quality projection not found"); }
}
