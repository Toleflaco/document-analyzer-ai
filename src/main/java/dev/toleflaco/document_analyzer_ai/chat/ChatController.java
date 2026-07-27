package dev.toleflaco.document_analyzer_ai.chat;

import dev.toleflaco.document_analyzer_ai.observability.LlmLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, LlmLoggingAdvisor loggingAdvisor) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),loggingAdvisor)
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam String conversationId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(a->a.param(ChatMemory.CONVERSATION_ID,conversationId))
                .call()
                .content();
    }
}
