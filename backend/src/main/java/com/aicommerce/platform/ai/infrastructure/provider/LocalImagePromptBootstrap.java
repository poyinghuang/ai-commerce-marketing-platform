package com.aicommerce.platform.ai.infrastructure.provider;

import com.aicommerce.platform.ai.application.AiPromptTemplateService;
import com.aicommerce.platform.ai.application.AppendPromptTemplateVersionCommand;
import com.aicommerce.platform.ai.application.CreatePromptTemplateCommand;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.ai.infrastructure.persistence.PromptTemplateJpaRepository;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class LocalImagePromptBootstrap implements ApplicationRunner {
    public static final String TEMPLATE_KEY = "image.background-composite-v1";
    private final PromptTemplateJpaRepository templates;
    private final AiPromptTemplateService service;
    private final AuditOperationContextFactory contexts;

    public LocalImagePromptBootstrap(PromptTemplateJpaRepository templates,
            AiPromptTemplateService service, AuditOperationContextFactory contexts) {
        this.templates = templates;
        this.service = service;
        this.contexts = contexts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (templates.findByTemplateKey(TEMPLATE_KEY).isPresent()) return;
        var context = contexts.forSystem("local-image-prompt-bootstrap");
        var template = service.createTemplate(new CreatePromptTemplateCommand(
                TEMPLATE_KEY, GenerationType.IMAGE, "Background Composite V1"), context);
        service.appendVersion(template.getPromptTemplateUuid(), new AppendPromptTemplateVersionCommand(
                "Generate a suitable environment grounded only in supplied Product context.",
                "Do not redraw, replace, distort, recolor, crop, or cover the Product.",
                "{\"type\":\"object\",\"properties\":{\"product\":{},\"creativePlan\":{},\"sourceAssetUuid\":{},\"maskAssetUuid\":{},\"workflowKey\":{}}}"), context);
    }
}
