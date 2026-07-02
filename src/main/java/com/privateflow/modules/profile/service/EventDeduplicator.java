package com.privateflow.modules.profile.service;

import com.privateflow.modules.profile.config.ProfileConfigProvider;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class EventDeduplicator {

  private static final int MAX_SIZE = 200;
  private final ProfileConfigProvider configProvider;
  private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

  public EventDeduplicator(ProfileConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public boolean seenRecently(String phone, String conversationSummary) {
    cleanup();
    String key = phone + ":" + md5(conversationSummary == null ? "" : conversationSummary);
    long now = Instant.now().toEpochMilli();
    Long previous = seen.put(key, now);
    return previous != null && now - previous <= configProvider.get().dedupWindowS() * 1000L;
  }

  private void cleanup() {
    long now = Instant.now().toEpochMilli();
    long ttl = 30_000L;
    Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Long> entry = iterator.next();
      if (now - entry.getValue() > ttl || seen.size() > MAX_SIZE) {
        iterator.remove();
      }
    }
  }

  @PreDestroy
  public void destroy() {
    seen.clear();
  }

  private String md5(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : bytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      return Integer.toHexString(text.hashCode());
    }
  }
}
