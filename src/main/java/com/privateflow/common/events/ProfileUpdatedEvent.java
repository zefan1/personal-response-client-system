package com.privateflow.common.events;

import java.util.List;

public record ProfileUpdatedEvent(String phone, List<String> updatedFields) {

  public ProfileUpdatedEvent(String phone) {
    this(phone, List.of());
  }
}
