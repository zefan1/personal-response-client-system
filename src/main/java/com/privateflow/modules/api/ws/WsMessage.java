package com.privateflow.modules.api.ws;

public record WsMessage(Long messageId, String type, Object payload) {

  public static WsMessage unsaved(String type, Object payload) {
    return new WsMessage(null, type, payload);
  }
}
