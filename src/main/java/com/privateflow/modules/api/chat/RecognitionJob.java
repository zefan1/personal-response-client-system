package com.privateflow.modules.api.chat;

import java.time.Instant;

final class RecognitionJob {

  private final String jobId;
  private final String username;
  private final String imageToken;
  private final ChatRecognizeRequest request;
  private final Instant createdAt;
  private RecognitionJobStatus status;
  private String errorCode;
  private ChatResponse response;
  private PendingReplyTaskView pendingTask;
  private Instant updatedAt;
  private boolean running;

  RecognitionJob(
      String jobId,
      String username,
      String imageToken,
      ChatRecognizeRequest request,
      Instant createdAt) {
    this.jobId = jobId;
    this.username = username;
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

  boolean unfinished() {
    return status == RecognitionJobStatus.QUEUED
        || status == RecognitionJobStatus.RECOGNIZING
        || status == RecognitionJobStatus.READY
        || status == RecognitionJobStatus.WAITING_CUSTOMER;
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
      pendingTask = completedResponse == null ? null : completedResponse.pendingTask();
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

  boolean expire(Instant now) {
    if (status != RecognitionJobStatus.QUEUED && status != RecognitionJobStatus.RECOGNIZING) {
      return false;
    }
    status = RecognitionJobStatus.EXPIRED;
    errorCode = "RECOGNITION_IMAGE_EXPIRED";
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
        pendingTask,
        createdAt,
        updatedAt);
  }
}
