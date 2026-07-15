package com.privateflow.modules.profile.service;

import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagSelectionContext;
import com.privateflow.modules.tags.TagSelectionValidationResult;
import com.privateflow.modules.tags.TagSelectionValidator;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TagAnalysisDecisionValidator {

  private final TagSelectionValidator selectionValidator;

  public TagAnalysisDecisionValidator(TagSelectionValidator selectionValidator) {
    this.selectionValidator = selectionValidator;
  }

  public ProfileAnalysisResult validate(ProfileAnalysisResult result, ProfileExtractRequest request) {
    if (result == null || request == null) {
      throw invalid("档案分析结果或请求不能为空");
    }
    ProfileAnalysisContext context = request.analysisContext();
    Map<String, ProfileAnalysisContext.CategoryCandidate> candidates = new LinkedHashMap<>();
    context.candidateCategories().forEach(category -> candidates.put(category.categoryCode(), category));
    Map<String, Set<String>> currentCodes = new LinkedHashMap<>();
    context.currentTags().forEach(tag -> currentCodes
        .computeIfAbsent(tag.categoryCode(), ignored -> new HashSet<>())
        .add(tag.tagCode()));
    Set<String> decidedCategories = new HashSet<>();

    for (TagAnalysisDecision decision : result.tagDecisions()) {
      if (decision == null || decision.categoryCode() == null || decision.categoryCode().isBlank()) {
        throw invalid("Skill 返回的分类编码不能为空");
      }
      if (!decidedCategories.add(decision.categoryCode())) {
        throw invalid("同一分类只能返回一条标签判断");
      }
      validateDecision(decision, candidates.get(decision.categoryCode()), currentCodes, context.effectiveMessageCount());
    }
    return result;
  }

  private void validateDecision(
      TagAnalysisDecision decision,
      ProfileAnalysisContext.CategoryCandidate candidate,
      Map<String, Set<String>> currentCodes,
      int effectiveMessageCount) {
    if (decision == null || candidate == null) {
      throw invalid("Skill 返回了本次未允许判断的分类");
    }
    if (decision.confidence() == null
        || decision.confidence().compareTo(BigDecimal.ZERO) < 0
        || decision.confidence().compareTo(BigDecimal.ONE) > 0) {
      throw invalid("Skill 返回的把握度必须在 0 到 1 之间");
    }
    if (decision.evidence() == null || decision.evidence().isBlank()) {
      throw invalid("Skill 返回的判断依据不能为空");
    }
    if (decision.resultType() == TagAnalysisResultType.UPDATE) {
      validateUpdate(decision, candidate, currentCodes, effectiveMessageCount);
      return;
    }
    if (!decision.tagCodes().isEmpty() || decision.requestedAction() != TagAnalysisAction.NONE) {
      throw invalid("无法判断或保持当前时不能返回标签值或更新动作");
    }
  }

  private void validateUpdate(
      TagAnalysisDecision decision,
      ProfileAnalysisContext.CategoryCandidate candidate,
      Map<String, Set<String>> currentCodes,
      int effectiveMessageCount) {
    TagAnalysisAction expectedAction = expectedAction(candidate.autoUpdateMode());
    if (decision.requestedAction() != expectedAction) {
      throw invalid("Skill 返回的更新动作与分类策略不一致");
    }
    Set<String> existing = currentCodes.getOrDefault(decision.categoryCode(), Set.of());
    if (decision.tagCodes().stream().anyMatch(existing::contains)) {
      throw invalid("多选分类只能返回尚未存在的新增标签");
    }
    TagSelectionValidationResult validation = selectionValidator.validateCodes(
        TagCandidatePurpose.SYSTEM_INFERENCE,
        decision.categoryCode(),
        decision.tagCodes(),
        new TagSelectionContext(
            decision.evidence(),
            effectiveMessageCount,
            decision.confidence(),
            null));
    if (!validation.accepted()) {
      throw invalid(validation.message());
    }
  }

  private TagAnalysisAction expectedAction(String autoUpdateMode) {
    return switch (autoUpdateMode) {
      case "ADD_ONLY" -> TagAnalysisAction.ADD;
      case "REPLACE" -> TagAnalysisAction.REPLACE;
      case "RECORD_ONLY" -> TagAnalysisAction.NONE;
      default -> throw invalid("分类自动更新策略非法：" + autoUpdateMode);
    };
  }

  private SkillGatewayException invalid(String message) {
    return new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, message, true);
  }
}
