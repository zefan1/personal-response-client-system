package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.auth.AuthUser;
import java.time.Duration;
import java.time.Instant;

final class RecognitionJob {

  private final String jobId;
  private final String username;
  private final AuthUser authUser;
  private final String imageToken;
  private final ChatRecognizeRequest request;
  private final Instant createdAt;
  private RecognitionJobStatus status;
  private String errorCode;
  private ChatResponse response;
  private Instant updatedAt;
  private boolean running;

  RecognitionJob(
      String jobId,
      String username,
      AuthUser authUser,
      String imageToken,
      ChatRecognizeRequest request,
      Instant createdAt) {
    this.jobId = jobId;
    this.username = username;
    this.authUser = authUser;
    this.imageToken = imageToken;
    this.request = request;
    this.createdAt = createdAt;
    this.status = RecognitionJobStatus.QUEUED;
    this.updatedAt = createdAt;
  }

  String jobId() {
    return jobId;
  }

  String username() {
    return username;
  }

  AuthUser authUser() {
    return authUser;
  }

  String imageToken() {
    return imageToken;
  }

  ChatRecognizeRequest request() {
    return request;
  }

  RecognitionJobStatus status() {
    return status;
  }

  boolean running() {
    return running;
  }

  boolean runningAndActive() {
    return running
        && status != RecognitionJobStatus.CANCELLED
        && status != RecognitionJobStatus.EXPIRED;
  }

  boolean unfinished() {
    return status == RecognitionJobStatus.QUEUED
        || status == RecognitionJobStatus.RECOGNIZING
        || status == RecognitionJobStatus.READY;
  }

  boolean requiresTemporaryImage() {
    return status == RecognitionJobStatus.QUEUED || running;
  }

  boolean resultExpired(Instant now, Duration retention) {
    if (now == null || retention == null || retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("recognition job retention is required");
    }
    return status == RecognitionJobStatus.READY
        && !updatedAt.plus(retention).isAfter(now);
  }

  boolean terminal() {
    return !running
        && (status == RecognitionJobStatus.FAILED
            || status == RecognitionJobStatus.CANCELLED
            || status == RecognitionJobStatus.EXPIRED);
  }

  Instant updatedAt() {
    return updatedAt;
  }

  void start(Instant now) {
    if (status != RecognitionJobStatus.QUEUED) {
      return;
    }
    status = RecognitionJobStatus.RECOGNIZING;
    running = true;
    updatedAt = now;
  }

  boolean cancel(Instant now) {
    if (status != RecognitionJobStatus.QUEUED && status != RecognitionJobStatus.RECOGNIZING) {
      return false;
    }
    boolean wasQueued = status == RecognitionJobStatus.QUEUED;
    status = RecognitionJobStatus.CANCELLED;
    updatedAt = now;
    return wasQueued;
  }

  boolean complete(ChatResponse completedResponse, RecognitionJobStatus completedStatus, Instant now) {
    if (!running) {
      return false;
    }
    running = false;
    if (status != RecognitionJobStatus.CANCELLED && status != RecognitionJobStatus.EXPIRED) {
      status = completedStatus;
      response = completedResponse;
    }
    updatedAt = now;
    return true;
  }

  boolean fail(String publicErrorCode, Instant now) {
    if (!running) {
      return false;
    }
    running = false;
    if (status != RecognitionJobStatus.CANCELLED && status != RecognitionJobStatus.EXPIRED) {
      status = RecognitionJobStatus.FAILED;
      errorCode = publicErrorCode;
    }
    updatedAt = now;
    return true;
  }

  boolean replaceResponse(ChatResponse replacement, Instant now) {
    if (status != RecognitionJobStatus.READY || replacement == null) {
      return false;
    }
    response = replacement;
    updatedAt = now;
    return true;
  }

  boolean expire(Instant now) {
    if (terminal()) {
      return false;
    }
    boolean cancelled = status == RecognitionJobStatus.CANCELLED;
    running = false;
    if (!cancelled) {
      status = RecognitionJobStatus.EXPIRED;
      errorCode = "RECOGNITION_IMAGE_EXPIRED";
    }
    response = null;
    updatedAt = now;
    return true;
  }

  RecognitionJobView view() {
    return new RecognitionJobView(
        jobId,
        request.replySessionId(),
        status,
        errorCode,
        response,
        createdAt,
        updatedAt);
  }
}
