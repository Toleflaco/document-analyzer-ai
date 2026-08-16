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
import java.util.Optional;

@RestController
public class AnalyzeController {

    private final ChatClient chatClient;
    private final String promptTemplateText;
    private final CvAnalysisCache cache;

    public AnalyzeController(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/analyze-cv.st") Resource promptResource,
            LlmLoggingAdvisor loggingAdvisor,
            CvAnalysisCache cache
    ) throws IOException {
        this.cache = cache;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(loggingAdvisor)
                .build();
        this.promptTemplateText = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    @PostMapping("/analyze")
    public CvSummary analyze(@RequestBody AnalyzeRequest request) {

        Optional<CvSummary> cached = cache.get(request.cv());
        if (cached.isPresent()) {
            return cached.get();
        }
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
        CvSummary summary = converter.convert(rawResponse);
        cache.put(request.cv(), summary);
        return summary;
    }
}
