package com.aicommerce.platform.quality.application;

import com.aicommerce.platform.common.application.FieldPatch;

public record ManualAdjustmentPatch(FieldPatch<Integer> manualAdjustment, FieldPatch<String> reason) {}
