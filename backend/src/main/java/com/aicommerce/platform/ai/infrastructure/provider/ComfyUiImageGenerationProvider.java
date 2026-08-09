package com.aicommerce.platform.ai.infrastructure.provider;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.io.InputStream;

import com.aicommerce.platform.ai.application.AiProviderException;
import com.aicommerce.platform.ai.application.ImageGenerationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Profile("comfyui & !local & !test & !production")
public class ComfyUiImageGenerationProvider implements ImageGenerationProvider {
    private static final int MAX_RESPONSE_BYTES = 16_777_216;
    private final URI origin;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final JsonNode workflowTemplate;

    public ComfyUiImageGenerationProvider(@Value("${COMFYUI_BASE_URL:}") String baseUrl, ObjectMapper mapper) {
        this(validateOrigin(baseUrl), mapper);
    }

    ComfyUiImageGenerationProvider(URI origin, ObjectMapper mapper) {
        this.origin = origin;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.client = RestClient.builder().baseUrl(origin.toString()).requestFactory(requestFactory).build();
        this.mapper = mapper;
        this.workflowTemplate = loadWorkflow(mapper);
    }

    @Override
    public ImageSubmission submit(ImageRequest request) {
        requireRequest(request);
        try {
            ObjectNode workflow = (ObjectNode) workflowTemplate.deepCopy();
            ObjectNode inputs = (ObjectNode) workflow.path("1").path("inputs");
            inputs.put("prompt", request.workflowInputs().getOrDefault("prompt", ""));
            inputs.put("source_png_base64", Base64.getEncoder().encodeToString(request.sourceBytes()));
            inputs.put("mask_png_base64", request.maskBytes() == null ? ""
                    : Base64.getEncoder().encodeToString(request.maskBytes()));
            inputs.put("width", request.width());
            inputs.put("height", request.height());
            inputs.put("filename_prefix", "stage03-" + request.generationJobUuid());
            Map<String, Object> prompt = Map.of("prompt", workflow,
                    "client_id", request.generationJobUuid().toString());
            JsonNode response = client.post().uri("/prompt").contentType(MediaType.APPLICATION_JSON)
                    .body(prompt).exchange((requestSpec, responseSpec) -> readJson(responseSpec, 1_048_576));
            String promptId = response == null ? null : response.path("prompt_id").asText(null);
            if (promptId == null || !promptId.matches("[A-Za-z0-9_-]{1,128}")) throw invalid();
            return new ImageSubmission(promptId, "comfyui-background-composite-v1", Map.of("workflowVersion", "1"));
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "ComfyUI submission failed");
        }
    }

    @Override
    public ImageResult await(ImageRequest request, ImageSubmission submission) {
        Duration boundedTimeout = request.timeout().compareTo(Duration.ofSeconds(60)) > 0
                ? Duration.ofSeconds(60) : request.timeout();
        long deadline = System.nanoTime() + boundedTimeout.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                JsonNode history = client.get().uri("/history/{id}", submission.providerJobId())
                        .exchange((requestSpec, responseSpec) -> readJson(responseSpec, 1_048_576));
                JsonNode outputs = history == null ? null : history.path(submission.providerJobId()).path("outputs");
                JsonNode image = firstImage(outputs);
                if (image != null) {
                    String filename = identifier(image.path("filename").asText(null), 256);
                    String subfolder = optionalIdentifier(image.path("subfolder").asText(""), 256);
                    String type = image.path("type").asText();
                    if (!"output".equals(type)) throw invalid();
                    byte[] bytes = client.get().uri(builder -> builder.path("/view")
                            .queryParam("filename", filename).queryParam("subfolder", subfolder)
                            .queryParam("type", "output").build())
                            .exchange((requestSpec, responseSpec) -> readBytes(responseSpec, MAX_RESPONSE_BYTES));
                    if (bytes == null || bytes.length == 0 || bytes.length > MAX_RESPONSE_BYTES) throw invalid();
                    return new ImageResult(bytes, mediaType(bytes), submission.modelLabel(), BigDecimal.ZERO,
                            List.of(), submission.metadata());
                }
                Thread.sleep(200);
            }
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "ComfyUI result timed out");
        } catch (AiProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "ComfyUI polling interrupted");
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "ComfyUI result retrieval failed");
        }
    }

    private JsonNode firstImage(JsonNode outputs) {
        if (outputs == null || !outputs.isObject()) return null;
        var fields = outputs.properties().iterator();
        while (fields.hasNext()) {
            JsonNode images = fields.next().getValue().path("images");
            if (images.isArray() && !images.isEmpty()) return images.get(0);
        }
        return null;
    }

    private void requireRequest(ImageRequest request) {
        if (!"background-composite-v1".equals(request.workflowKey()) || !"1".equals(request.workflowVersion())
                || request.sourceBytes() == null || request.sourceBytes().length > MAX_RESPONSE_BYTES
                || request.width() < 1 || request.width() > 4096 || request.height() < 1 || request.height() > 4096
                || !"png".equals(request.format())) throw invalid();
    }

    private String mediaType(byte[] bytes) {
        if (bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) return "image/png";
        if (bytes.length > 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8) return "image/jpeg";
        throw invalid();
    }

    private String identifier(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9._-]+")) throw invalid();
        return value;
    }

    private String optionalIdentifier(String value, int max) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() > max || !value.matches("[A-Za-z0-9_./-]+") || value.contains("..") || value.startsWith("/")) throw invalid();
        return value;
    }

    private AiProviderException invalid() {
        return new AiProviderException("AI_OUTPUT_INVALID", "ComfyUI returned an invalid response");
    }

    private static URI validateOrigin(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (!uri.isAbsolute() || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("COMFYUI_BASE_URL must be a fixed HTTP(S) origin");
            }
            return URI.create(uri.getScheme() + "://" + uri.getAuthority());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("ComfyUI is not safely configured", exception);
        }
    }

    private static JsonNode loadWorkflow(ObjectMapper mapper) {
        try (InputStream stream = ComfyUiImageGenerationProvider.class.getResourceAsStream(
                "/ai/workflows/background-composite-v1.json")) {
            if (stream == null) throw new IllegalStateException("ComfyUI workflow resource is missing");
            JsonNode workflow = mapper.readTree(stream);
            if (!workflow.isObject() || !"Stage03BackgroundComposite".equals(
                    workflow.path("1").path("class_type").asText())) {
                throw new IllegalStateException("ComfyUI workflow resource is invalid");
            }
            return workflow;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("ComfyUI workflow resource cannot be loaded", exception);
        }
    }

    private JsonNode readJson(org.springframework.http.client.ClientHttpResponse response, int maximumBytes) {
        try {
            return mapper.readTree(readBytes(response, maximumBytes));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private byte[] readBytes(org.springframework.http.client.ClientHttpResponse response, int maximumBytes) {
        try {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "ComfyUI request failed");
            }
            byte[] bytes = response.getBody().readNBytes(maximumBytes + 1);
            if (bytes.length == 0 || bytes.length > maximumBytes) throw invalid();
            return bytes;
        } catch (AiProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "ComfyUI response could not be read");
        }
    }
}
