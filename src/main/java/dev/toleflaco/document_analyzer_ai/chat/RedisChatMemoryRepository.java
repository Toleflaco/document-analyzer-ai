package dev.toleflaco.document_analyzer_ai.chat;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> findConversationIds() {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX+"*").build();
        List<String> ids = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String fullKey = cursor.next();
                String id = fullKey.substring(KEY_PREFIX.length());
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;

        List<String> jsons = redis.opsForList().range(key, 0, -1);
        if (jsons == null || jsons.isEmpty()) {
            return List.of();
        }

        return jsons.stream()
                .map(json -> objectMapper.readValue(json, MessageRecord.class))
                .map(record -> toMessage(record))
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        redis.delete(key);
        if (messages.isEmpty()) {
            return;
        }
        // convertir messages a List<String> de JSONs
        List<String> values = messages.stream()
                .map(m -> new MessageRecord(m.getMessageType().name(), m.getText()))
                .map(r -> objectMapper.writeValueAsString(r))
                .toList();
        // rightPushAll con esa lista
        redis.opsForList().rightPushAll(key, values);
        // expire
        redis.expire(key, TTL);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redis.delete(key);
    }

    private Message toMessage(MessageRecord r) {
        // switch sobre record.messageType() que devuelva UserMessage, AssistantMessage o SystemMessage
        return switch (r.messageType()) {
            case "USER" -> new UserMessage(r.text());
            case "ASSISTANT" -> new AssistantMessage(r.text());
            case "SYSTEM" -> new SystemMessage(r.text());
            default -> throw new IllegalStateException("Unexpected value: " + r.messageType());
        };
    }
}
