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
public class LocalTextPromptBootstrap implements ApplicationRunner {

    public static final String TEMPLATE_KEY = "copy.product-default";

    private final PromptTemplateJpaRepository templates;
    private final AiPromptTemplateService promptService;
    private final AuditOperationContextFactory contexts;

    public LocalTextPromptBootstrap(PromptTemplateJpaRepository templates,
            AiPromptTemplateService promptService, AuditOperationContextFactory contexts) {
        this.templates = templates;
        this.promptService = promptService;
        this.contexts = contexts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (templates.findByTemplateKey(TEMPLATE_KEY).isPresent()) return;
        var context = contexts.forSystem("local-text-prompt-bootstrap");
        var template = promptService.createTemplate(new CreatePromptTemplateCommand(
                TEMPLATE_KEY, GenerationType.TEXT, "Default Product Copy"), context);
        promptService.appendVersion(template.getPromptTemplateUuid(), new AppendPromptTemplateVersionCommand(
                "Create concise, accurate marketing copy grounded only in the supplied Product context.",
                null,
                "{\"type\":\"object\",\"properties\":{\"product\":{},\"knowledge\":{},\"creativePlan\":{},\"variationIndex\":{}}}"),
                context);
    }
}
