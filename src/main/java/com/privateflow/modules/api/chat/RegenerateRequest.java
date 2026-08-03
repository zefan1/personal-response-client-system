package com.privateflow.modules.api.chat;

public record RegenerateRequest(String phone, Long customerId) {

  public RegenerateRequest(String phone) {
    this(phone, null);
  }
}
