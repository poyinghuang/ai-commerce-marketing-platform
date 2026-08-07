package com.aicommerce.platform.quality.application;

import com.aicommerce.platform.quality.domain.ReadinessStatus;
import com.aicommerce.platform.quality.domain.WorkflowStatus;

record WorkflowSnapshot(ReadinessStatus status, String reason) {
    static WorkflowSnapshot from(WorkflowStatus value) {
        return new WorkflowSnapshot(value.getStatus(), value.getStatusReason());
    }
}
