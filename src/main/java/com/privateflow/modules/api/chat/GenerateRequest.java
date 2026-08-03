package com.privateflow.modules.api.chat;

public record GenerateRequest(String phone, String scene, String clientMessage, Long customerId) {

  public GenerateRequest(String phone, String scene, String clientMessage) {
    this(phone, scene, clientMessage, null);
  }
}
