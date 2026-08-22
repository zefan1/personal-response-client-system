package com.privateflow.modules.communication;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.llm.LlmSummaryInput;
import com.privateflow.modules.llm.LlmSummaryService;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationSummaryProcessor {

  private static final int MESSAGE_BATCH_SIZE = 100;
  private final CommunicationArchiveRepository repository;
  private final LlmSummaryService summaryService;
  private final ProfileWriter profileWriter;
  private final Clock clock;
  private final Duration retryDelay;

  @Autowired
  public CommunicationSummaryProcessor(
      CommunicationArchiveRepository repository,
      LlmSummaryService summaryService,
      ProfileWriter profileWriter) {
    this(
        repository,
        summaryService,
        profileWriter,
        Clock.systemDefaultZone(),
        Duration.ofMinutes(5));
  }

  CommunicationSummaryProcessor(
      CommunicationArchiveRepository repository,
      LlmSummaryService summaryService,
      ProfileWriter profileWriter,
      Clock clock,
      Duration retryDelay) {
    this.repository = repository;
    this.summaryService = summaryService;
    this.profileWriter = profileWriter;
    this.clock = clock;
    this.retryDelay = retryDelay;
  }

  @Transactional
  public void process(long customerId) {
    CommunicationSummaryState state = repository.findSummaryState(customerId)
        .orElse(new CommunicationSummaryState(
            customerId, "PENDING", null, 0, null, null, LocalDateTime.now(clock)));
    LocalDateTime now = LocalDateTime.now(clock);
    try {
      long lastSummarizedMessageId = state.lastSummarizedMessageId() == null
          ? 0L
          : state.lastSummarizedMessageId();
      List<ArchivedCommunicationMessage> messages =
          repository.findMessagesAfter(customerId, lastSummarizedMessageId, MESSAGE_BATCH_SIZE);
      if (messages.isEmpty()) {
        throw new IllegalStateException("no new communication messages are available");
      }
      String previousSummary = repository.findSummaryVersions(customerId).stream()
          .findFirst()
          .map(CommunicationSummaryVersion::summaryText)
          .orElse("");
      String phone = repository.findCustomerPhone(customerId)
          .orElseThrow(() -> new IllegalStateException("customer is not available"));
      String summary = summaryService.trySummarize(new LlmSummaryInput(
              phone,
              "",
              "",
              messages.stream().map(this::toChatMessage).toList(),
              previousSummary.isBlank() ? "" : "上一版沟通汇总：" + previousSummary,
              "",
              "communication-summary"))
          .filter(value -> !value.isBlank())
          .orElseThrow(() -> new IllegalStateException("communication summary is unavailable"));
      long lastMessageId = messages.get(messages.size() - 1).id();
      repository.appendSummaryVersion(customerId, summary, lastMessageId, now);
      profileWriter.write(
          phone,
          Map.of("customerProfileSummary", summary),
          null,
          true,
          CustomerFieldHistoryContext.of("沟通汇总", "客户沟通消息", "SYSTEM"));
    } catch (RuntimeException ex) {
      repository.markSummaryFailed(
          customerId,
          state.retryCount() + 1,
          now.plus(retryDelay),
          ex.getMessage());
    }
  }

  private CustomerMessageSentEvent.ChatMessage toChatMessage(ArchivedCommunicationMessage message) {
    return new CustomerMessageSentEvent.ChatMessage(
        message.senderRole(),
        message.currentText(),
        message.messageTime() == null ? "" : message.messageTime().toString());
  }
}
