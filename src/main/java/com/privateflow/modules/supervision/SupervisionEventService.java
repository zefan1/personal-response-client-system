package com.privateflow.modules.supervision;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.chat.AiUsageRequest;
import com.privateflow.modules.api.chat.ChatReplySource;
import com.privateflow.modules.api.chat.ReplyTaskClock;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupervisionEventService {

  private static final int MAX_COPIED_REPLY_CHARS = 4000;
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

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
