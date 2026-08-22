package com.privateflow.modules.api.chat;

import com.privateflow.modules.communication.ArchivedCommunicationBatch;
import com.privateflow.modules.communication.CommunicationArchiveRepository;
import com.privateflow.modules.communication.CommunicationBatchDraft;
import com.privateflow.modules.communication.CommunicationDeduplicationService;
import com.privateflow.modules.communication.CommunicationMessageDraft;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.image.Message;
import com.privateflow.modules.image.RecognitionResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecognitionCommunicationArchiveService {

  private static final int RECENT_DEDUPE_MESSAGE_LIMIT = 100;
  private final CommunicationArchiveRepository repository;
  private final CommunicationDeduplicationService deduplicationService;
  private final CustomerRepository customerRepository;
  private final Clock clock;

  @Autowired
  public RecognitionCommunicationArchiveService(
      CommunicationArchiveRepository repository,
      CommunicationDeduplicationService deduplicationService,
      CustomerRepository customerRepository) {
    this(repository, deduplicationService, customerRepository, Clock.systemDefaultZone());
  }

  RecognitionCommunicationArchiveService(
      CommunicationArchiveRepository repository,
      CommunicationDeduplicationService deduplicationService,
      CustomerRepository customerRepository,
      Clock clock) {
    this.repository = repository;
    this.deduplicationService = deduplicationService;
    this.customerRepository = customerRepository;
    this.clock = clock;
  }

  @Transactional
  public Customer createRecognitionCustomer(ChatRecognizeRequest request, RecognitionResult recognized) {
    Customer customer = new Customer();
    customer.setPhone(recognized == null ? null : recognized.phone());
    customer.setNickname(recognized == null ? null : recognized.nickname());
    customer.setSourceChannel(platformCode(recognized == null ? null : recognized.platform()));
    customer.setLeadType(request == null ? null : request.leadType());
    customer.setAssignedKeeper(AuthContext.username());
    customer.setCustomerStage("待联系");
    customer.setSourceTable(request == null ? null : request.sourceTable());
    return customerRepository.createRecognitionCustomer(customer);
  }

  @Transactional
  public Customer createPendingSendCustomer(SendConfirmRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("send confirmation request is required");
    }
    String phone = blank(request.phone()) ? null : request.phone().trim();
    if (phone != null) {
      Customer existing = customerRepository.findByPhone(phone).orElse(null);
      if (existing != null) {
        return existing;
      }
    }
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(blank(request.nickname()) ? null : request.nickname().trim());
    customer.setLeadType(request.leadType());
    customer.setSourceTable(request.sourceTable());
    customer.setAssignedKeeper(AuthContext.username());
    customer.setCustomerStage("待联系");
    return customerRepository.createRecognitionCustomer(customer);
  }

  @Transactional
  public void archiveConfirmedEmployeeMessage(Customer customer, String sentText, String username) {
    if (customer == null || customer.getId() == null || blank(sentText) || blank(username)) {
      throw new IllegalArgumentException("customer, operator, and sent text are required");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    String platform = platformCode(customer.getSourceChannel());
    CommunicationMessageDraft message = new CommunicationMessageDraft(
        "EMPLOYEE", sentText.trim(), "TEXT", now, false);
    repository.archive(new CommunicationBatchDraft(
        username,
        platform,
        null,
        customer.getNickname(),
        customer.getPhone(),
        customer.getId(),
        "EMPLOYEE: " + sentText.trim(),
        now,
        List.of(message)));
    repository.markSummaryPending(customer.getId(), now);
  }

  @Transactional
  public ArchivedCommunicationBatch archive(
      ChatRecognizeRequest request,
      RecognitionResult recognized,
      Customer customer,
      String username) {
    LocalDateTime recognizedAt = LocalDateTime.now(clock);
    List<CommunicationMessageDraft> captured = capturedMessages(request, recognized, recognizedAt);
    CommunicationBatchDraft scope = new CommunicationBatchDraft(
        username,
        platformCode(recognized == null ? null : recognized.platform()),
        recognized == null ? null : recognized.customerIdentifier(),
        recognized == null ? request.customerIdentifier() : recognized.nickname(),
        recognized == null ? null : recognized.phone(),
        customer == null ? null : customer.getId(),
        rawText(captured),
        recognizedAt,
        captured);
    List<CommunicationMessageDraft> recent =
        repository.findRecentMessageDrafts(scope, RECENT_DEDUPE_MESSAGE_LIMIT);
    List<CommunicationMessageDraft> deduplicated =
        deduplicationService.removeOverlappingPrefix(recent, captured);
    CommunicationBatchDraft archivedDraft = new CommunicationBatchDraft(
        scope.username(),
        scope.platformCode(),
        scope.platformIdentifier(),
        scope.recognizedNickname(),
        scope.recognizedPhone(),
        scope.customerId(),
        scope.rawText(),
        scope.recognizedAt(),
        deduplicated);
    ArchivedCommunicationBatch archived = repository.archive(archivedDraft);
    if (customer != null && customer.getId() != null) {
      repository.markSummaryPending(customer.getId(), recognizedAt);
    }
    return archived;
  }

  private List<CommunicationMessageDraft> capturedMessages(
      ChatRecognizeRequest request,
      RecognitionResult recognized,
      LocalDateTime recognizedAt) {
    List<CommunicationMessageDraft> messages = new ArrayList<>();
    if (request != null && request.rawMessages() != null && !request.rawMessages().isEmpty()) {
      for (ChatMessageDto message : request.rawMessages()) {
        add(messages, message == null ? null : message.role(), message == null ? null : message.text(),
            message == null ? null : message.timestamp(), recognizedAt);
      }
      return List.copyOf(messages);
    }
    if (recognized != null && recognized.messages() != null && !recognized.messages().isEmpty()) {
      for (Message message : recognized.messages()) {
        add(messages, message == null ? null : message.role(), message == null ? null : message.text(),
            recognized.timestamp(), recognizedAt);
      }
      return List.copyOf(messages);
    }
    if (request != null && !blank(request.textMessage())) {
      add(messages, "client", request.textMessage(), null, recognizedAt);
    }
    return List.copyOf(messages);
  }

  private void add(
      List<CommunicationMessageDraft> target,
      String rawRole,
      String text,
      String rawTime,
      LocalDateTime recognizedAt) {
    String role = senderRole(rawRole);
    if (role == null || blank(text)) {
      return;
    }
    LocalDateTime messageTime = parseTime(rawTime, recognizedAt);
    boolean estimated = messageTime == null;
    target.add(new CommunicationMessageDraft(
        role,
        text.trim(),
        mediaMarker(text) ? "MEDIA_MARKER" : "TEXT",
        estimated ? recognizedAt : messageTime,
        estimated));
  }

  private String rawText(List<CommunicationMessageDraft> messages) {
    return messages.stream()
        .map(message -> message.senderRole() + ": " + message.text())
        .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
  }

  private LocalDateTime parseTime(String value, LocalDateTime recognizedAt) {
    if (blank(value)) {
      return null;
    }
    String trimmed = value.trim();
    try {
      return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME);
    } catch (DateTimeParseException ignored) {
      // Continue with time-only formats returned by screenshot recognition.
    }
    for (DateTimeFormatter formatter : List.of(
        DateTimeFormatter.ofPattern("H:mm:ss"),
        DateTimeFormatter.ofPattern("H:mm"))) {
      try {
        LocalTime time = LocalTime.parse(trimmed, formatter);
        LocalDate date = recognizedAt.toLocalDate();
        return LocalDateTime.of(date, time);
      } catch (DateTimeParseException ignored) {
        // Try the next supported format.
      }
    }
    return null;
  }

  private String senderRole(String role) {
    if (blank(role)) {
      return null;
    }
    return switch (role.trim().toLowerCase(Locale.ROOT)) {
      case "client", "customer", "sender", "user", "客户", "顾客", "用户", "对方" -> "CUSTOMER";
      case "keeper", "staff", "assistant", "service", "agent", "员工", "管家", "自己", "我" -> "EMPLOYEE";
      default -> null;
    };
  }

  private String platformCode(String platform) {
    String value = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
    if (value.contains("WECOM") || value.contains("WEWORK") || value.contains("企业微信")) {
      return "WECOM";
    }
    if (value.contains("WECHAT") || value.contains("微信")) {
      return "WECHAT";
    }
    if (value.contains("DOUYIN") || value.contains("抖音")) {
      return "DOUYIN";
    }
    if (value.contains("XIAOHONGSHU") || value.contains("RED") || value.contains("小红书")) {
      return "XIAOHONGSHU";
    }
    if (value.contains("MEITUAN") || value.contains("美团")) {
      return "MEITUAN";
    }
    return "OTHER";
  }

  private boolean mediaMarker(String text) {
    String value = text == null ? "" : text;
    return value.contains("发送了一张图片")
        || value.contains("发送了一条语音")
        || value.contains("发送了一个视频")
        || value.contains("发送了一个文件")
        || value.contains("商品卡片");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
