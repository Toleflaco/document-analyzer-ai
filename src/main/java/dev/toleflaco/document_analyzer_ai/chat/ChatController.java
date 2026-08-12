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
        // TODO(bloque 4): reintroducir LlmLoggingAdvisor cuando se resuelva
        //   la duplicación de mensajes en Redis causada por su interacción
        //   con MessageChatMemoryAdvisor. Diagnóstico pendiente:
        //   posiblemente relacionado con getOrder() y la posición en la
        //   cadena de advisors. Chat funcional sin él.
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam String conversationId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
