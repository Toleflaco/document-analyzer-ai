package dev.toleflaco.document_analyzer_ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DocumentAnalyzerAiApplication {

	@Bean
	ChatMemory chatMemory() {
		return MessageWindowChatMemory.builder()
				.maxMessages(10)
				.build();
	}
	public static void main(String[] args) {
		SpringApplication.run(DocumentAnalyzerAiApplication.class, args);
	}

}
