package dev.toleflaco.document_analyzer_ai.analyze;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AnalyzeController {

    private static final String TEMPLATE = """
            Analiza el siguiente CV y extrae información estructurada.
            Responde únicamente con el formato solicitado, sin comentarios adicionales.

            CV:
            {cv}

            {format}
            """;

    private final ChatClient chatClient;

    public AnalyzeController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/analyze")
    public CvSummary analyze(@RequestBody AnalyzeRequest request) {
        BeanOutputConverter<CvSummary> converter = new BeanOutputConverter<>(CvSummary.class);

        PromptTemplate promptTemplate = new PromptTemplate(TEMPLATE);
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
