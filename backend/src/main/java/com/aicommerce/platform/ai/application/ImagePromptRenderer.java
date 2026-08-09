package com.aicommerce.platform.ai.application;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aicommerce.platform.ai.domain.PromptTemplateVersion;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.product.domain.Product;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ImagePromptRenderer {
    private final ObjectMapper mapper;

    public ImagePromptRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Rendered render(PromptTemplateVersion version, Product product, CreativePlan plan,
            java.util.UUID sourceAssetUuid, java.util.UUID maskAssetUuid, String workflowKey) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("product", Map.of(
                "productId", safe(product.getProductId()),
                "productName", safe(product.getProductName()),
                "brand", safe(product.getBrand()),
                "category", safe(product.getCategory()),
                "shortDescription", safe(product.getShortDescription())));
        snapshot.put("creativePlan", Map.of(
                "planName", safe(plan.getPlanName()),
                "creativeAngle", safe(plan.getCreativeAngle()),
                "visualStyle", safe(plan.getVisualStyle()),
                "mainColor", safe(plan.getMainColor()),
                "brandTone", safe(plan.getBrandTone())));
        snapshot.put("sourceAssetUuid", sourceAssetUuid.toString());
        if (maskAssetUuid != null) snapshot.put("maskAssetUuid", maskAssetUuid.toString());
        snapshot.put("workflowKey", workflowKey);
        String json = mapper.writeValueAsString(snapshot)
                .replace("&", "\\u0026").replace("<", "\\u003c").replace(">", "\\u003e");
        String prompt = version.getTemplateText() + "\n\n<untrusted-product-context>\n" + json
                + "\n</untrusted-product-context>\nGenerate background/environment only. Preserve Product pixels exactly.";
        if (prompt.length() > 16000) throw new AiGenerationException("AI_PROMPT_INPUT_INVALID", "Image prompt is too long");
        return new Rendered(prompt, json);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record Rendered(String prompt, String snapshot) {
    }
}
