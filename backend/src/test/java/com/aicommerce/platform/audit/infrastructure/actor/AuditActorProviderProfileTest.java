package com.aicommerce.platform.audit.infrastructure.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Stream;

import com.aicommerce.platform.audit.application.AuditActorProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AuditActorProviderProfileTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileCombinations")
    void selectsExactlyOneFailSafeActorProvider(
            String scenario,
            String[] activeProfiles,
            Class<? extends AuditActorProvider> expectedProvider) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(activeProfiles);
            context.register(LocalAdminAuditActorProvider.class, DenyMutationAuditActorProvider.class);
            context.refresh();

            Map<String, AuditActorProvider> providers = context.getBeansOfType(AuditActorProvider.class);
            assertThat(providers).hasSize(1);
            AuditActorProvider provider = providers.values().iterator().next();
            assertThat(provider).isExactlyInstanceOf(expectedProvider);

            if (expectedProvider == LocalAdminAuditActorProvider.class) {
                assertThat(provider.currentActor().id()).isEqualTo("local-admin");
            } else {
                assertThatThrownBy(provider::currentActor)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("trusted production AuditActorProvider");
            }
        }
    }

    private static Stream<Arguments> profileCombinations() {
        return Stream.of(
                Arguments.of("local", new String[] {"local"}, LocalAdminAuditActorProvider.class),
                Arguments.of("test", new String[] {"test"}, LocalAdminAuditActorProvider.class),
                Arguments.of("default", new String[0], DenyMutationAuditActorProvider.class),
                Arguments.of("production", new String[] {"production"}, DenyMutationAuditActorProvider.class),
                Arguments.of(
                        "production,local",
                        new String[] {"production", "local"},
                        DenyMutationAuditActorProvider.class),
                Arguments.of(
                        "production,test",
                        new String[] {"production", "test"},
                        DenyMutationAuditActorProvider.class));
    }
}
