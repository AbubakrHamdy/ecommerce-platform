package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

// Renders every email twice: HTML for mail clients and plain text as the fallback spam filters expect.
@Component
public class EmailRenderer {

    private static final String TEMPLATE = "notification";

    private final ITemplateEngine htmlEngine;
    private final SpringTemplateEngine textEngine;
    private final EmailProperties properties;

    public EmailRenderer(ITemplateEngine htmlEngine, EmailProperties properties) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine();
        this.properties = properties;
    }

    public EmailMessage render(String to, String subject, EmailContent content) {
        Context context = new Context(Locale.ENGLISH, Map.of(
                "storeName", properties.storeName(),
                "subject", subject,
                "content", content));
        return new EmailMessage(to, subject,
                htmlEngine.process("email/" + TEMPLATE, context),
                textEngine.process(TEMPLATE, context));
    }

    // A Spring engine (SpEL), so text templates use exactly the same expressions as the HTML one.
    private static SpringTemplateEngine textEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
