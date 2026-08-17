package com.aicommerce.platform.delivery.application.port;
import java.math.BigDecimal; import java.util.*; import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
public record PlatformBudgetPolicy(String currency,PlatformBudgetType budgetType,BigDecimal maxEntityAmount){public PlatformBudgetPolicy{Objects.requireNonNull(budgetType);Objects.requireNonNull(maxEntityAmount);if(!"TWD".equals(currency)||maxEntityAmount.signum()<=0||maxEntityAmount.scale()>6)throw new IllegalArgumentException("PLATFORM_POLICY_REJECTED");}}
