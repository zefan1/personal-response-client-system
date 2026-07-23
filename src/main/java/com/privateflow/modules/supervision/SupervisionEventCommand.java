package com.privateflow.modules.supervision;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SupervisionEventCommand {

  private final String eventId;
  private final SupervisionEventType eventType;
  private final String operatorUsername;
  private final String customerPhone;
  private final String channelCode;
  private final String channelAccount;
  private final String leadSource;
  private final String assignedKeeper;
  private final String scene;
  private final String taskId;
  private final String replySessionId;
  private final String replySource;
  private final String dedupeKey;
  private final String generatedReplySnapshot;
  private final String copiedReplySnapshot;
  private final Map<String, Object> metadata;
  private final LocalDateTime occurredAt;

  private SupervisionEventCommand(
      String eventId,
      SupervisionEventType eventType,
      String operatorUsername,
      String customerPhone,
      String channelCode,
      String channelAccount,
      String leadSource,
      String assignedKeeper,
      String scene,
      String taskId,
      String replySessionId,
      String replySource,
      String dedupeKey,
      String generatedReplySnapshot,
      String copiedReplySnapshot,
      Map<String, Object> metadata,
      LocalDateTime occurredAt) {
    if (eventId == null || eventId.isBlank()) {
      throw new IllegalArgumentException("eventId is required");
    }
    if (eventType == null) {
      throw new IllegalArgumentException("eventType is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    this.eventId = eventId;
    this.eventType = eventType;
    this.operatorUsername = operatorUsername;
    this.customerPhone = customerPhone;
    this.channelCode = channelCode;
    this.channelAccount = channelAccount;
    this.leadSource = leadSource;
    this.assignedKeeper = assignedKeeper;
    this.scene = scene;
    this.taskId = taskId;
    this.replySessionId = replySessionId;
    this.replySource = replySource;
    this.dedupeKey = dedupeKey;
    this.generatedReplySnapshot = generatedReplySnapshot;
    this.copiedReplySnapshot = copiedReplySnapshot;
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    this.occurredAt = occurredAt;
  }

  public static SupervisionEventCommand replyCopied(
      String eventId,
      String operatorUsername,
      String customerPhone,
      String channelCode,
      String channelAccount,
      String leadSource,
      String assignedKeeper,
      String scene,
      String taskId,
      String replySessionId,
      String replySource,
      String dedupeKey,
      String generatedReplySnapshot,
      String copiedReplySnapshot,
      Long customerId,
      String leadType,
      String customerStage,
      LocalDateTime occurredAt) {
    return new SupervisionEventCommand(
        eventId,
        SupervisionEventType.REPLY_COPIED,
        operatorUsername,
        customerPhone,
        channelCode,
        channelAccount,
        leadSource,
        assignedKeeper,
        scene,
        taskId,
        replySessionId,
        replySource,
        dedupeKey,
        generatedReplySnapshot,
        copiedReplySnapshot,
        replyCopiedMetadata(customerId, leadType, customerStage),
        occurredAt);
  }

  public String eventId() {
    return eventId;
  }

  public SupervisionEventType eventType() {
    return eventType;
  }

  public String operatorUsername() {
    return operatorUsername;
  }

  public String customerPhone() {
    return customerPhone;
  }

  public String channelCode() {
    return channelCode;
  }

  public String channelAccount() {
    return channelAccount;
  }

  public String leadSource() {
    return leadSource;
  }

  public String assignedKeeper() {
    return assignedKeeper;
  }

  public String scene() {
    return scene;
  }

  public String taskId() {
    return taskId;
  }

  public String replySessionId() {
    return replySessionId;
  }

  public String replySource() {
    return replySource;
  }

  public String dedupeKey() {
    return dedupeKey;
  }

  public String generatedReplySnapshot() {
    return generatedReplySnapshot;
  }

  public String copiedReplySnapshot() {
    return copiedReplySnapshot;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  public LocalDateTime occurredAt() {
    return occurredAt;
  }

  private static Map<String, Object> replyCopiedMetadata(
      Long customerId,
      String leadType,
      String customerStage) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (customerId != null) {
      metadata.put("customerId", customerId);
    }
    putIfPresent(metadata, "leadType", leadType);
    putIfPresent(metadata, "customerStage", customerStage);
    return metadata;
  }

  private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
    if (value != null && !value.isBlank()) {
      metadata.put(key, value.trim());
    }
  }
}
