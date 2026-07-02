package com.privateflow.modules.api.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.config.SystemConfigProvider;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RequestContextStore {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final SystemConfigProvider configProvider;

  public RequestContextStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, SystemConfigProvider configProvider) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.configProvider = configProvider;
  }

  public void save(String username, String phone, RequestContext context) {
    try {
      redisTemplate.opsForValue().set(key(username, phone), objectMapper.writeValueAsString(context), Duration.ofSeconds(configProvider.get().requestContextTtlS()));
    } catch (Exception ex) {
      // Redis context cache degrades open.
    }
  }

  public Optional<RequestContext> read(String username, String phone) {
    try {
      String raw = redisTemplate.opsForValue().get(key(username, phone));
      return raw == null ? Optional.empty() : Optional.of(objectMapper.readValue(raw, RequestContext.class));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  private String key(String username, String phone) {
    return "request:" + username + ":" + phone;
  }
}
