package com.aicommerce.platform.connector.drive.application;

import java.util.UUID;

public record StorageEnsureRequest(UUID productUuid, String productId) {}
