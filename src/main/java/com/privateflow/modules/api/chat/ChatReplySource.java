package com.privateflow.modules.api.chat;

public record ChatReplySource(
    String source,
    String label,
    String detail
) {
  public static ChatReplySource llm() {
    return new ChatReplySource("LLM", "LLM 生成", "回复建议来自 LLM 场景路由");
  }

  public static ChatReplySource llmWithSkill() {
    return new ChatReplySource("LLM", "LLM 生成", "已先执行 Skill 固定流程，再由当前 LLM 生成最终回复");
  }

  public static ChatReplySource skill() {
    return new ChatReplySource("SKILL", "Skill 生成", "回复建议来自 Skill 场景接口");
  }

  public static ChatReplySource fallback(String detail) {
    return new ChatReplySource("FALLBACK", "系统兜底", detail == null || detail.isBlank() ? "AI 服务不可用，已使用降级回复" : detail);
  }
}
