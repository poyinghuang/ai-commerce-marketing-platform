package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class GoogleSheetValuesProviderCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        if (environment.matchesProfiles("production")
                || (!environment.matchesProfiles("local") && !environment.matchesProfiles("test"))) {
            return true;
        }
        return "google".equals(environment.getProperty("platform.sheets.provider"));
    }
}
