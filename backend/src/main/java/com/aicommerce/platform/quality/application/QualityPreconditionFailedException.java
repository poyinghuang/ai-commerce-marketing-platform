package com.aicommerce.platform.quality.application;

public class QualityPreconditionFailedException extends RuntimeException {
    public QualityPreconditionFailedException() { super("Quality version does not match If-Match"); }
}
