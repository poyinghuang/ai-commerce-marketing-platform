package com.aicommerce.platform.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.aicommerce.platform.ai.application.AiProviderException;
import com.aicommerce.platform.ai.application.AiCostCeilingProvider;
import com.aicommerce.platform.ai.application.ImageGenerationProvider;
import com.aicommerce.platform.ai.application.AssetBinaryStore;
import com.aicommerce.platform.ai.application.TextGenerationProvider;
import com.aicommerce.platform.ai.domain.GenerationType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AiGenerationProviderProfileTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileCombinations")
    void selectsExactlyOneProviderForEachPort(String scenario, String[] profiles, boolean expectStub) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.register(
                    StubTextGenerationProvider.class,
                    StubImageGenerationProvider.class,
                    DeterministicAiCostCeilingProvider.class,
                    DenyTextGenerationProvider.class,
                    DenyImageGenerationProvider.class,
                    StubAssetBinaryStore.class,
                    DenyAssetBinaryStore.class,
                    DenyAiCostCeilingProvider.class);
            context.refresh();

            Map<String, TextGenerationProvider> textProviders = context.getBeansOfType(TextGenerationProvider.class);
            Map<String, ImageGenerationProvider> imageProviders = context.getBeansOfType(ImageGenerationProvider.class);
            Map<String, AiCostCeilingProvider> costProviders = context.getBeansOfType(AiCostCeilingProvider.class);
            Map<String, AssetBinaryStore> binaryStores = context.getBeansOfType(AssetBinaryStore.class);
            assertThat(textProviders).hasSize(1);
            assertThat(imageProviders).hasSize(1);
            assertThat(costProviders).hasSize(1);
            assertThat(binaryStores).hasSize(1);

            TextGenerationProvider textProvider = textProviders.values().iterator().next();
            ImageGenerationProvider imageProvider = imageProviders.values().iterator().next();
            AiCostCeilingProvider costProvider = costProviders.values().iterator().next();
            AssetBinaryStore binaryStore = binaryStores.values().iterator().next();
            if (expectStub) {
                assertThat(textProvider).isExactlyInstanceOf(StubTextGenerationProvider.class);
                assertThat(imageProvider).isExactlyInstanceOf(StubImageGenerationProvider.class);
                assertThat(costProvider).isExactlyInstanceOf(DeterministicAiCostCeilingProvider.class);
                assertThat(binaryStore).isExactlyInstanceOf(StubAssetBinaryStore.class);
                assertThat(textProvider.generate(textRequest()).actualCost()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(imageProvider.submit(imageRequest()).providerJobId()).startsWith("stub-image-");
                assertThat(costProvider.ceilingFor(GenerationType.TEXT, "stub", "stub-text").worstCaseCost())
                        .isEqualByComparingTo("2.000000");
            } else {
                assertThat(textProvider).isExactlyInstanceOf(DenyTextGenerationProvider.class);
                assertThat(imageProvider).isExactlyInstanceOf(DenyImageGenerationProvider.class);
                assertThat(costProvider).isExactlyInstanceOf(DenyAiCostCeilingProvider.class);
                assertThat(binaryStore).isExactlyInstanceOf(DenyAssetBinaryStore.class);
                assertNotConfigured(() -> textProvider.generate(textRequest()));
                assertNotConfigured(() -> imageProvider.submit(imageRequest()));
                assertNotConfigured(() -> costProvider.ceilingFor(GenerationType.TEXT, "stub", "stub-text"));
            }
        }
    }

    private void assertNotConfigured(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).code())
                .isEqualTo("AI_PROVIDER_NOT_CONFIGURED");
    }

    private TextGenerationProvider.TextRequest textRequest() {
        return new TextGenerationProvider.TextRequest(UUID.randomUUID(), "prompt", "stub", 256, Duration.ofSeconds(5));
    }

    private ImageGenerationProvider.ImageRequest imageRequest() {
        return new ImageGenerationProvider.ImageRequest(
                UUID.randomUUID(),
                "background-composite",
                "1",
                Map.of("background", "studio"),
                "source-handle",
                "mask-handle",
                new byte[] {1},
                new byte[] {1},
                512,
                512,
                "png",
                Duration.ofSeconds(5));
    }

    private static Stream<Arguments> profileCombinations() {
        return Stream.of(
                Arguments.of("local", new String[] {"local"}, true),
                Arguments.of("test", new String[] {"test"}, true),
                Arguments.of("default", new String[0], false),
                Arguments.of("production", new String[] {"production"}, false),
                Arguments.of("production,local", new String[] {"production", "local"}, false),
                Arguments.of("production,test", new String[] {"production", "test"}, false),
                Arguments.of("production,comfyui", new String[] {"production", "comfyui"}, false));
    }
}
