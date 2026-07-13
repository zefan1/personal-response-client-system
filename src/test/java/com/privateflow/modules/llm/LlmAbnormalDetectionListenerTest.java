package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.FollowupWsMessageReadyEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class LlmAbnormalDetectionListenerTest {

  private LlmAbnormalDetectionService detectionService;
  private CustomerQueryService customerQueryService;
  private ApplicationEventPublisher eventPublisher;
  private LlmAbnormalDetectionListener listener;

  @BeforeEach
  void setUp() {
    detectionService = Mockito.mock(LlmAbnormalDetectionService.class);
    customerQueryService = Mockito.mock(CustomerQueryService.class);
    eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    listener = new LlmAbnormalDetectionListener(detectionService, customerQueryService, eventPublisher);
  }

  @Test
  void skipsWhenDetectionDisabled() {
    when(detectionService.enabled()).thenReturn(false);

    listener.onCustomerMessageSent(event());

    verify(detectionService, never()).tryDetect(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void publishesAbnormalAlertToAssignedKeeper() {
    when(detectionService.enabled()).thenReturn(true);
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setAssignedKeeper("keeper-2");
    customer.setLeadType("TUAN_GOU");
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(detectionService.tryDetect(any())).thenReturn(Optional.of(new LlmAbnormalAlert(
        "CHURN_RISK",
        "WARN",
        "客户明确表示可能不再到店")));

    listener.onCustomerMessageSent(event());

    ArgumentCaptor<FollowupWsMessageReadyEvent> captor = ArgumentCaptor.forClass(FollowupWsMessageReadyEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    FollowupWsMessageReadyEvent published = captor.getValue();
    assertThat(published.userId()).isEqualTo("keeper-2");
    assertThat(published.type()).isEqualTo("ABNORMAL_ALERT");
    assertThat(String.valueOf(published.payload())).contains("CHURN_RISK");
    assertThat(String.valueOf(published.payload())).contains("18800001111");
  }

  private CustomerMessageSentEvent event() {
    return new CustomerMessageSentEvent(
        "18800001111",
        "Alice",
        false,
        "私域客资表",
        "TUAN_GOU",
        "客户说可能不来了",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "我不想来了", "12:00")),
        "我再帮您看看",
        "NEXT_STEP",
        null,
        "keeper-1");
  }
}
