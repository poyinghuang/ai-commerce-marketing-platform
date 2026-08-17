package com.aicommerce.platform.delivery.application.port;
public interface PlatformAdPort { PlatformWriteOutcome submitAd(PlatformAdCommand command); PlatformWriteOutcome changeAdState(PlatformStateMutationCommand command); }
