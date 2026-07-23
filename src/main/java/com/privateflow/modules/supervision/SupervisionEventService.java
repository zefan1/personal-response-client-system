package com.privateflow.modules.supervision;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.chat.AiUsageRequest;
import com.privateflow.modules.api.chat.ChatReplySource;
import com.privateflow.modules.api.chat.PendingReplyTask;
import com.privateflow.modules.api.chat.ReplyTaskClock;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupervisionEventService {

  private static final int MAX_COPIED_REPLY_CHARS = 4000;
  private static final int MAX_GENERATED_REPLY_CHARS = 4000;
  private static final String CHAT_RECOGNIZE_SCENE = "CHAT_RECOGNIZE";
  private static final int MAX_TASK_ID_CHARS = 36;
  private static final int MAX_REPLY_SESSION_ID_CHARS = 80;
  private static final int MAX_REPLY_SOURCE_CHARS = 64;

  private final CustomerRepository customerRepository;
  private final CustomerAccessService customerAccessService;
  private final SupervisionEventRepository eventRepository;
  private final ReplyTaskClock taskClock;

  public SupervisionEventService(
      CustomerRepository customerRepository,
      CustomerAccessService customerAccessService,
      SupervisionEventRepository eventRepository) {
    this(customerRepository, customerAccessService, eventRepository, new ReplyTaskClock());
  }

  @Autowired
  SupervisionEventService(
      CustomerRepository customerRepository,
      CustomerAccessService customerAccessService,
      SupervisionEventRepository eventRepository,
      ReplyTaskClock taskClock) {
    this.customerRepository = customerRepository;
    this.customerAccessService = customerAccessService;
    this.eventRepository = eventRepository;
    this.taskClock = taskClock;
  }

  SupervisionEventService(
      CustomerRepository customerRepository,
      CustomerAccessService customerAccessService,
      SupervisionEventRepository eventRepository,
      Clock clock) {
    this(customerRepository, customerAccessService, eventRepository, new ReplyTaskClock(clock));
  }

  public Map<String, Object> recordAiUsage(AiUsageRequest request) {
    validateRequest(request);
    AuthUser operator = AuthContext.current();
    if (operator == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "authentication is required");
    }
    String phone = request.phone().trim();
    Customer customer = customerRepository.findByPhone(phone)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found"));
    if (!customerAccessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to this customer");
    }

    eventRepository.insert(SupervisionEventCommand.replyCopied(
        UUID.randomUUID().toString(),
        operator.username(),
        customer.getPhone(),
        trimToNull(customer.getSourceChannel()),
        null,
        trimToNull(customer.getSourceTable()),
        trimToNull(customer.getAssignedKeeper()),
        null,
        trimToNull(request.taskId()),
        trimToNull(request.replySessionId()),
        trimToNull(request.replySource()),
        null,
        null,
        request.copiedText(),
        customer.getId(),
        customer.getLeadType(),
        customer.getCustomerStage(),
        taskClock.now()));
    return Map.of("recorded", true, "semantic", "COPIED_AI_REPLY");
  }

  public void recordTaskCreated(PendingReplyTask task) {
    if (task == null) {
      return;
    }
    int candidateCount = task.candidatePhones() == null ? 0 : task.candidatePhones().size();
    insertWorkflowEvent(
        SupervisionEventType.TASK_CREATED,
        task.username(),
        null,
        task.sourceTable(),
        CHAT_RECOGNIZE_SCENE,
        task.taskId(),
        task.replySessionId(),
        null,
        null,
        null,
        Map.of("candidateCount", candidateCount));
  }

  public void recordPendingEntered(Customer customer, String taskId) {
    if (customer == null || blank(customer.getPhone())) {
      return;
    }
    LocalDateTime occurredAt = taskClock.now();
    LocalDate date = occurredAt.toLocalDate();
    String dedupeKey = "PENDING:" + customer.getPhone().trim() + ":" + date;
    try {
      insertWorkflowEvent(
          SupervisionEventType.PENDING_ENTERED,
          AuthContext.username(),
          customer,
          null,
          CHAT_RECOGNIZE_SCENE,
          taskId,
          null,
          null,
          dedupeKey,
          null,
          customerMetadata(customer),
          occurredAt);
    } catch (DuplicateKeyException ignored) {
      // The per-customer daily pending entry is intentionally idempotent.
    }
  }

  public void recordCustomerSelected(Customer customer, String taskId, String replySessionId) {
    if (customer == null) {
      return;
    }
    insertWorkflowEvent(
        SupervisionEventType.CUSTOMER_SELECTED,
        AuthContext.username(),
        customer,
        null,
        CHAT_RECOGNIZE_SCENE,
        taskId,
        replySessionId,
        null,
        null,
        null,
        customerMetadata(customer));
  }

  public void recordGeneratedReply(
      Customer customer,
      String scene,
      String taskId,
      String replySessionId,
      ChatReplySource replySource,
      SkillResponse response) {
    insertWorkflowEvent(
        SupervisionEventType.REPLY_GENERATED,
        AuthContext.username(),
        customer,
        null,
        trimToNull(scene),
        taskId,
        replySessionId,
        replySource == null ? null : trimToNull(replySource.source()),
        null,
        generatedReplySnapshot(response),
        customerMetadata(customer));
  }

  public void recordRecognitionFailed(
      String leadType,
      String sourceTable,
      String replySessionId,
      String publicErrorCode) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfPresent(metadata, "leadType", leadType);
    putIfPresent(metadata, "errorCode", publicErrorCode);
    insertWorkflowEvent(
        SupervisionEventType.RECOGNITION_FAILED,
        AuthContext.username(),
        null,
        sourceTable,
        CHAT_RECOGNIZE_SCENE,
        null,
        replySessionId,
        null,
        null,
        null,
        metadata);
  }

  private void validateRequest(AiUsageRequest request) {
    if (request == null || blank(request.phone()) || blank(request.copiedText())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone and copiedText are required");
    }
    if (request.copiedText().length() > MAX_COPIED_REPLY_CHARS) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "copiedText exceeds 4000 characters");
    }
    validateOptionalLength("taskId", request.taskId(), MAX_TASK_ID_CHARS);
    validateOptionalLength("replySessionId", request.replySessionId(), MAX_REPLY_SESSION_ID_CHARS);
    validateOptionalLength("replySource", request.replySource(), MAX_REPLY_SOURCE_CHARS);
    validateReplySource(request.replySource());
  }

  private void validateOptionalLength(String field, String value, int maxLength) {
    String normalized = trimToNull(value);
    if (normalized != null && normalized.length() > maxLength) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, field + " exceeds " + maxLength + " characters");
    }
  }

  private void validateReplySource(String value) {
    String source = trimToNull(value);
    if (source == null
        || (!ChatReplySource.llm().source().equals(source)
            && !ChatReplySource.skill().source().equals(source)
            && !ChatReplySource.fallback(null).source().equals(source))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "replySource is invalid");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private void insertWorkflowEvent(
      SupervisionEventType eventType,
      String operatorUsername,
      Customer customer,
      String leadSource,
      String scene,
      String taskId,
      String replySessionId,
      String replySource,
      String dedupeKey,
      String generatedReplySnapshot,
      Map<String, Object> metadata) {
    insertWorkflowEvent(
        eventType,
        operatorUsername,
        customer,
        leadSource,
        scene,
        taskId,
        replySessionId,
        replySource,
        dedupeKey,
        generatedReplySnapshot,
        metadata,
        taskClock.now());
  }

  private void insertWorkflowEvent(
      SupervisionEventType eventType,
      String operatorUsername,
      Customer customer,
      String leadSource,
      String scene,
      String taskId,
      String replySessionId,
      String replySource,
      String dedupeKey,
      String generatedReplySnapshot,
      Map<String, Object> metadata,
      LocalDateTime occurredAt) {
    eventRepository.insert(SupervisionEventCommand.workflow(
        UUID.randomUUID().toString(),
        eventType,
        trimToNull(operatorUsername),
        customer == null ? null : trimToNull(customer.getPhone()),
        customer == null ? null : trimToNull(customer.getSourceChannel()),
        customer == null ? trimToNull(leadSource) : trimToNull(customer.getSourceTable()),
        customer == null ? null : trimToNull(customer.getAssignedKeeper()),
        trimToNull(scene),
        trimToNull(taskId),
        trimToNull(replySessionId),
        trimToNull(replySource),
        trimToNull(dedupeKey),
        trimToNull(generatedReplySnapshot),
        metadata,
        occurredAt));
  }

  private Map<String, Object> customerMetadata(Customer customer) {
    if (customer == null) {
      return Map.of();
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (customer.getId() != null) {
      metadata.put("customerId", customer.getId());
    }
    putIfPresent(metadata, "leadType", customer.getLeadType());
    putIfPresent(metadata, "customerStage", customer.getCustomerStage());
    return metadata;
  }

  private String generatedReplySnapshot(SkillResponse response) {
    if (response == null || response.suggestions() == null) {
      return null;
    }
    String snapshot = response.suggestions().stream()
        .filter(java.util.Objects::nonNull)
        .map(Suggestion::text)
        .filter(value -> !blank(value))
        .map(String::trim)
        .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    if (snapshot.isBlank()) {
      return null;
    }
    return snapshot.length() <= MAX_GENERATED_REPLY_CHARS
        ? snapshot
        : snapshot.substring(0, MAX_GENERATED_REPLY_CHARS);
  }

  private void putIfPresent(Map<String, Object> metadata, String key, String value) {
    String normalized = trimToNull(value);
    if (normalized != null) {
      metadata.put(key, normalized);
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
