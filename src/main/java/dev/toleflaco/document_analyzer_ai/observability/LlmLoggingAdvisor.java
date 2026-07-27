package dev.toleflaco.document_analyzer_ai.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LlmLoggingAdvisor implements CallAdvisor {

    private final double inputCostPerMillionTokens;
    private final double outputCostPerMillionTokens;
    private static final Logger log = LoggerFactory.getLogger(LlmLoggingAdvisor.class);

    public LlmLoggingAdvisor(@Value("${llm.pricing.input-per-mtok}") double inputCostPerMillionTokens, @Value("${llm.pricing.output-per-mtok}") double outputCostPerMillionTokens) {

        this.inputCostPerMillionTokens = inputCostPerMillionTokens;
        this.outputCostPerMillionTokens = outputCostPerMillionTokens;

    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {

        long start = System.nanoTime();
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        long end = System.nanoTime();
        long durationNanos = end - start;
        long durationMs = durationNanos / 1_000_000;
        // Extraer tokens
        Usage usage = response.chatResponse().getMetadata().getUsage();
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        double coste_usd = (promptTokens / 1_000_000.0) * inputCostPerMillionTokens + (completionTokens / 1_000_000.0) * outputCostPerMillionTokens;
        /*log.atInfo()
                .setMessage("llm call completed")
                .addKeyValue("latency_ms", durationMs)
                .addKeyValue("tokens_in", promptTokens)
                .addKeyValue("tokens_out", completionTokens)
                .addKeyValue("cost_usd", coste_usd)
                .log();

         */
        log.info("llm call completed latency_ms={} tokens_in={} tokens_out={} cost_usd={}",
                durationMs, promptTokens, completionTokens, String.format("%.6f", coste_usd));
        return response;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
