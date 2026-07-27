package dev.toleflaco.document_analyzer_ai.analyze;

import dev.toleflaco.document_analyzer_ai.observability.LlmLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class AnalyzePdfController {

    private final ChatClient chatClient;
    private final String promptTemplateText;

    public AnalyzePdfController(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/analyze-cv-pdf.st") Resource promptResource,
            LlmLoggingAdvisor loggingAdvisor
    ) throws IOException {
        this.chatClient = chatClientBuilder.defaultAdvisors(loggingAdvisor).build();
        this.promptTemplateText = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    @PostMapping("/analyze/pdf")
    public CvSummary analyzePdf(@RequestParam("file") MultipartFile file) {
        BeanOutputConverter<CvSummary> converter = new BeanOutputConverter<>(CvSummary.class);

        PromptTemplate promptTemplate = new PromptTemplate(promptTemplateText);
        String renderedPrompt = promptTemplate.render(Map.of(
                "format", converter.getFormat()
        ));

        Media pdfMedia = new Media(
                new MimeType("application", "pdf"),
                file.getResource()
        );

        String rawResponse = chatClient
                .prompt()
                .user(u -> u.text(renderedPrompt).media(pdfMedia))
                .call()
                .content();

        return converter.convert(rawResponse);
    }
}
