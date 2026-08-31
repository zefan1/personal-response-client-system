package com.privateflow.modules.skill.service;

public record SkillConnection(String baseUrl, String apiKey, String protocol) {

  public boolean configured() {
    return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
  }
}
