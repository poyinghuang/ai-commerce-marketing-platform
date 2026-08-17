package com.aicommerce.platform.delivery.application.port;
public interface PlatformAdSetPort { PlatformWriteOutcome submitAdSet(PlatformAdSetCommand command); PlatformWriteOutcome changeAdSetState(PlatformStateMutationCommand command); PlatformWriteOutcome updateAdSetBudget(PlatformBudgetMutationCommand command); }
