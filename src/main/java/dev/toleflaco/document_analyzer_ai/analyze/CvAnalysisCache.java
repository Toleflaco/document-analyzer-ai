package dev.toleflaco.document_analyzer_ai.analyze;

// imports que averigüas tú

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class CvAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(CvAnalysisCache.class);
    private static final String KEY_PREFIX = "analyze:cv:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CvAnalysisCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<CvSummary> get(String cvText) {
        String key = buildKey(cvText);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            CvSummary cached = objectMapper.readValue(json, CvSummary.class);
            return Optional.of(cached);
        } catch (Exception e) {
            log.warn("Redis GET failed for key {}, treating as cache miss", key, e);
            return Optional.empty();
        }
    }

    public void put(String cvText, CvSummary summary) {
        String key = buildKey(cvText);
        try {
            String json = objectMapper.writeValueAsString(summary);
            redis.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("Redis PUT failed for key {}, cache not populated", key, e);
        }
    }

    private String buildKey(String cvText) {
        String normalized = cvText.trim();
        String hash = sha256Hex(normalized);
        return KEY_PREFIX + hash;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
