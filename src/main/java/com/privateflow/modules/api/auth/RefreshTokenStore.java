package com.privateflow.modules.api.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

  private final StringRedisTemplate redisTemplate;

  public RefreshTokenStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public String issue(String username, Duration ttl) {
    String token = UUID.randomUUID().toString();
    try {
      redisTemplate.opsForValue().set(key(username), token, ttl);
    } catch (RuntimeException ex) {
      // Redis is allowed to degrade for local development; token remains usable only in response.
    }
    return token;
  }

  public Optional<String> read(String username) {
    try {
      return Optional.ofNullable(redisTemplate.opsForValue().get(key(username)));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  private String key(String username) {
    return "refresh:" + username;
  }
}
