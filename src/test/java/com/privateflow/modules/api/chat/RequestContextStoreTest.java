package com.privateflow.modules.api.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.config.SystemConfig;
import com.privateflow.modules.api.config.SystemConfigProvider;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RequestContextStoreTest {

  @Test
  void savesAndReadsContextByCustomerIdWithoutPhone() {
    StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
    SystemConfigProvider config = org.mockito.Mockito.mock(SystemConfigProvider.class);
    when(redis.opsForValue()).thenReturn(values);
    SystemConfig settings = org.mockito.Mockito.mock(SystemConfig.class);
    when(settings.requestContextTtlS()).thenReturn(900);
    when(config.get()).thenReturn(settings);
    RequestContextStore store = new RequestContextStore(redis, new ObjectMapper(), config);
    RequestContext context = new RequestContext(null, new SkillResponse(null, null, null, null), 0);

    store.save("keeper", 44L, context);

    verify(values).set(eq("request:keeper:customer:44"), any(String.class), any(Duration.class));
  }
}
