package com.aicommerce.platform.delivery.application.port;
import java.util.UUID; import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
public interface PlatformBudgetPolicyProvider { PlatformBudgetPolicy requirePolicy(UUID platformAccountUuid,PlatformBudgetType budgetType); }
