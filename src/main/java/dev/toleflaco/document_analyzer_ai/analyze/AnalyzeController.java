package dev.toleflaco.document_analyzer_ai.analyze;

import dev.toleflaco.document_analyzer_ai.observability.LlmLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class AnalyzeController {

    private final ChatClient chatClient;
    private final String promptTemplateText;

    public AnalyzeController(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/analyze-cv.st") Resource promptResource, LlmLoggingAdvisor loggingAdvisor
    ) throws IOException {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(loggingAdvisor)
                .build();
        this.promptTemplateText = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    @PostMapping("/analyze")
    public CvSummary analyze(@RequestBody AnalyzeRequest request) {
        BeanOutputConverter<CvSummary> converter = new BeanOutputConverter<>(CvSummary.class);

        PromptTemplate promptTemplate = new PromptTemplate(promptTemplateText);
        String renderedPrompt = promptTemplate.render(Map.of(
                "cv", request.cv(),
                "format", converter.getFormat()
        ));

        String rawResponse = chatClient
                .prompt()
                .user(renderedPrompt)
                .call()
                .content();

        return converter.convert(rawResponse);
    }
}
