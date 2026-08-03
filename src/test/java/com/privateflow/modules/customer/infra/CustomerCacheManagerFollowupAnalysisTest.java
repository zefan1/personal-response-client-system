package com.privateflow.modules.customer.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class CustomerCacheManagerFollowupAnalysisTest {

  @Test
  void writesTheNewAnalysisFieldsIntoCustomerCache() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    customer.setInternalNote("内部提醒");
    customer.setCustomerProfileSummary("客户B档案");
    customer.setFirstTrackingCapture("首次捕捉");
    customer.setSecondTrackingCapture("第二次捕捉");
    customer.setThirdTrackingCapture("第三次捕捉");
    CustomerCacheManager manager = new CustomerCacheManager(
        redisTemplate,
        mock(CustomerRepository.class),
        new CustomerCacheProperties());

    manager.write(customer);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(hashOperations).putAll(eq("customer:13800000000"), captor.capture());
    assertThat(captor.getValue())
        .containsEntry("internalNote", "内部提醒")
        .containsEntry("customerProfileSummary", "客户B档案")
        .containsEntry("firstTrackingCapture", "首次捕捉")
        .containsEntry("secondTrackingCapture", "第二次捕捉")
        .containsEntry("thirdTrackingCapture", "第三次捕捉");
  }
}
