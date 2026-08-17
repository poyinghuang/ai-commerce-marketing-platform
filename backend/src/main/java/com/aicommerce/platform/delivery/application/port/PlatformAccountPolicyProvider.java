package com.aicommerce.platform.delivery.application.port;
import java.util.UUID;
public interface PlatformAccountPolicyProvider { PlatformAccountPolicy requirePolicy(UUID platformAccountUuid); }
