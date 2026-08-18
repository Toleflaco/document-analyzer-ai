package dev.toleflaco.document_analyzer_ai.analyze;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class CvAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(CvAnalysisCache.class);
    private static final String KEY_PREFIX_TEXT = "analyze:cv:";
    private static final String KEY_PREFIX_PDF = "analyze:pdf:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CvAnalysisCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // === API pública para texto ===
    public Optional<CvSummary> get(String cvText) {
        return getInternal(buildKeyFromText(cvText));
    }

    public void put(String cvText, CvSummary summary) {
        putInternal(buildKeyFromText(cvText), summary);
    }

    // === API pública para PDF ===
    public Optional<CvSummary> get(byte[] pdfBytes) {
        return getInternal(buildKeyFromPdf(pdfBytes));
    }

    public void put(byte[] pdfBytes, CvSummary summary) {
        putInternal(buildKeyFromPdf(pdfBytes), summary);
    }

    // === Corazón privado (una sola implementación) ===
    private Optional<CvSummary> getInternal(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, CvSummary.class));
        } catch (Exception e) {
            log.warn("Redis GET failed for key {}, treating as cache miss", key, e);
            return Optional.empty();
        }
    }

    private void putInternal(String key, CvSummary summary) {
        try {
            String json = objectMapper.writeValueAsString(summary);
            redis.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("Redis PUT failed for key {}, cache not populated", key, e);
        }
    }

    private String buildKeyFromText(String cvText) {
        return KEY_PREFIX_TEXT + sha256Hex(cvText.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String buildKeyFromPdf(byte[] pdfBytes) {
        return KEY_PREFIX_PDF + sha256Hex(pdfBytes);
    }

    private static String sha256Hex(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
