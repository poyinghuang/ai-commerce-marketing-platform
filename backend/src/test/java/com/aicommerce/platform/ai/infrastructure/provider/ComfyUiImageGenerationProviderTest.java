package com.aicommerce.platform.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.ai.application.AiProviderException;
import com.aicommerce.platform.ai.application.ImageGenerationProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ComfyUiImageGenerationProviderTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesOnlyFixedRoutesRepositoryWorkflowAndValidatedOutputIdentifiers() {
        byte[] png = StubAssetBinaryStore.fixture();
        server.createContext("/prompt", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("Stage03BackgroundComposite", "filename_prefix", "source_png_base64");
            respond(exchange, 200, "{\"prompt_id\":\"prompt_123\"}".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/history/prompt_123", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            respond(exchange, 200, ("{\"prompt_123\":{\"outputs\":{\"9\":{\"images\":[{"
                    + "\"filename\":\"safe.png\",\"subfolder\":\"stage03\",\"type\":\"output\"}]}}}}")
                    .getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/view", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            respond(exchange, 200, png);
        });
        var provider = provider();
        var request = request(png);

        var submission = provider.submit(request);
        var result = provider.await(request, submission);

        assertThat(result.bytes()).isEqualTo(png);
        assertThat(requests).containsExactly(
                "/prompt",
                "/history/prompt_123",
                "/view?filename=safe.png&subfolder=stage03&type=output");
    }

    @Test
    void rejectsRedirectTraversalAndOversizedProviderBodies() {
        server.createContext("/prompt", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://example.invalid/steal");
            respond(exchange, 302, new byte[0]);
        });
        assertProviderCode(() -> provider().submit(request(StubAssetBinaryStore.fixture())),
                "AI_PROVIDER_UNAVAILABLE");

        server.removeContext("/prompt");
        server.createContext("/prompt", exchange -> respond(exchange, 200,
                ("x".repeat(1_048_577)).getBytes(StandardCharsets.UTF_8)));
        assertProviderCode(() -> provider().submit(request(StubAssetBinaryStore.fixture())),
                "AI_OUTPUT_INVALID");
    }

    @Test
    void rejectsUnsafeOriginBeforeAnyRequest() {
        assertThatThrownBy(() -> new ComfyUiImageGenerationProvider(
                "http://user:pass@localhost:8188/path?target=x", new JsonMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ComfyUI is not safely configured");
    }

    private ComfyUiImageGenerationProvider provider() {
        return new ComfyUiImageGenerationProvider(
                "http://127.0.0.1:" + server.getAddress().getPort(), new JsonMapper());
    }

    private ImageGenerationProvider.ImageRequest request(byte[] source) {
        return new ImageGenerationProvider.ImageRequest(UUID.randomUUID(), "background-composite-v1", "1",
                Map.of("prompt", "clean studio"), "source", null, source, null,
                4, 4, "png", Duration.ofSeconds(2));
    }

    private void assertProviderCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(AiProviderException.class)
                .extracting(value -> ((AiProviderException) value).code()).isEqualTo(code);
    }

    private void respond(HttpExchange exchange, int status, byte[] bytes) throws java.io.IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
