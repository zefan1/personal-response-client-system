package com.privateflow.modules.skill.service;

import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileExtractRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProfileAnalysisPromptBuilder {

  private static final String PROFILE_ANALYSIS_TASK = """
      你是客户档案分析助手。请分析 profile_analysis_context 中的客户档案、当前标签、锁定分类和动态候选标签。
      只能把 recentMessages 中 role=client 的客户原话或明确业务数据作为判断依据，不能把员工回复当作客户证据。
      只能返回 target_fields 中的非标签档案字段；标签判断必须使用 candidateCategories 中当前提供的分类和标签编码。
      UPDATE 表示证据充分且满足分类策略；信息不足返回 UNABLE_TO_DETERMINE；当前值仍正确返回 KEEP_CURRENT。
      多选 ADD_ONLY 只能返回尚未存在的新增标签并使用 ADD；单选 REPLACE 使用 REPLACE；不修改时使用 NONE。
      """;
  private static final String PROFILE_ANALYSIS_OUTPUT_CONTRACT = """
      Return JSON only with this exact top-level schema:
      {
        "profile_updates": {
          "fields": {},
          "tag_decisions": [
            {
              "category_code": "category code from candidateCategories",
              "tag_codes": ["tag code from that category"],
              "confidence": 0.95,
              "evidence": "customer quote or business evidence",
              "result_type": "UPDATE|UNABLE_TO_DETERMINE|KEEP_CURRENT",
              "requested_action": "ADD|REPLACE|NONE"
            }
          ]
        }
      }
      Use only enabled candidates supplied in profile_analysis_context. Do not return chain-of-thought.
      """;

  public ProfileAnalysisPrompt build(ProfileExtractRequest request, String additionalInstructions) {
    ProfileAnalysisContext context = request == null
        ? ProfileAnalysisContext.empty()
        : request.analysisContext();
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("client_message", profileClientMessage(context));
    input.put("chat_context", context.recentMessages());
    input.put("customer", context.customerProfile());
    input.put("target_fields", request == null || request.targetFields() == null ? List.of() : request.targetFields());
    input.put("profile_analysis_context", context);
    return new ProfileAnalysisPrompt(systemPrompt(additionalInstructions), input);
  }

  private String systemPrompt(String additionalInstructions) {
    String additional = additionalInstructions == null ? "" : additionalInstructions.strip();
    return PROFILE_ANALYSIS_TASK.strip()
        + (additional.isBlank() ? "" : "\n\n附加业务要求：\n" + additional)
        + "\n\n"
        + PROFILE_ANALYSIS_OUTPUT_CONTRACT.strip();
  }

  private String profileClientMessage(ProfileAnalysisContext context) {
    return context.recentMessages().stream()
        .filter(message -> "client".equals(message.role()))
        .map(ProfileAnalysisContext.ConversationMessage::text)
        .filter(text -> text != null && !text.isBlank())
        .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
  }

  public record ProfileAnalysisPrompt(String systemPrompt, Map<String, Object> input) {
    public ProfileAnalysisPrompt {
      input = input == null ? Map.of() : Map.copyOf(input);
    }
  }
}
