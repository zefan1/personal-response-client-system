package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.config.SystemConfigProvider;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

  private final StringRedisTemplate redisTemplate;
  private final SystemConfigProvider configProvider;

  public LoginRateLimiter(StringRedisTemplate redisTemplate, SystemConfigProvider configProvider) {
    this.redisTemplate = redisTemplate;
    this.configProvider = configProvider;
  }

  public boolean locked(String ip) {
    try {
      String raw = redisTemplate.opsForValue().get(key(ip));
      int count = raw == null ? 0 : Integer.parseInt(raw);
      return count >= configProvider.get().loginFailLimit();
    } catch (RuntimeException ex) {
      return false;
    }
  }

  public void recordFailure(String ip) {
    try {
      Long count = redisTemplate.opsForValue().increment(key(ip));
      if (count != null && count == 1L) {
        redisTemplate.expire(key(ip), Duration.ofMinutes(configProvider.get().loginLockMinutes()));
      }
    } catch (RuntimeException ex) {
      // Redis login throttling degrades open.
    }
  }

  public void clear(String ip) {
    try {
      redisTemplate.delete(key(ip));
    } catch (RuntimeException ex) {
      // Ignore Redis cleanup failures.
    }
  }

  private String key(String ip) {
    return "login:fail:" + ip;
  }
}
