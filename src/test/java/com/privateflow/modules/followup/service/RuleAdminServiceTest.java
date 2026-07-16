package com.privateflow.modules.followup.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import com.privateflow.modules.followup.RuleRequest;
import com.privateflow.modules.followup.infra.FollowupRuleRepository;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagSelectionValidationResult;
import com.privateflow.modules.tags.TagSelectionValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RuleAdminServiceTest {

  @Test
  void createRejectsUnknownConditionFieldBeforeSaving() {
    FollowupRuleRepository repository = Mockito.mock(FollowupRuleRepository.class);
    when(repository.nameExists("bad", null)).thenReturn(false);
    RuleAdminService service = new RuleAdminService(
        repository,
        new ObjectMapper(),
        Mockito.mock(RuleLoader.class),
        Mockito.mock(AuditLogger.class),
        new ConditionEvaluator(new ObjectMapper()),
        Mockito.mock(TagSelectionValidator.class));

    RuleRequest request = new RuleRequest(
        "bad",
        "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"unsupportedField\",\"op\":\"EQ\",\"value\":\"x\"}]}",
        ActionType.ALERT,
        "{}",
        10,
        true);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(FollowupException.class)
        .extracting(ex -> ((FollowupException) ex).getErrorCode())
        .isEqualTo(FollowupErrorCodes.CONDITION_PARSE_FAILED);
  }

  @Test
  void createValidatesEveryTagConditionAgainstFollowupPurpose() {
    FollowupRuleRepository repository = Mockito.mock(FollowupRuleRepository.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    TagSelectionValidationResult accepted = Mockito.mock(TagSelectionValidationResult.class);
    when(accepted.accepted()).thenReturn(true);
    when(repository.nameExists("tag rule", null)).thenReturn(false);
    when(repository.create(any())).thenReturn(9L);
    when(repository.findById(9L)).thenReturn(java.util.Optional.of(new com.privateflow.modules.followup.FollowupRule(
        9L, "tag rule", "{}", ActionType.ALERT, "{}", 10, true, false, null, null)));
    when(validator.validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L, 52L)), any()))
        .thenReturn(accepted);
    RuleAdminService service = service(repository, validator);

    service.create(new RuleRequest(
        "tag rule",
        "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51,52],\"match\":\"ANY\"}]}",
        ActionType.ALERT,
        "{}",
        10,
        true));

    verify(validator).validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L, 52L)), any());
  }

  @Test
  void createRejectsTagConditionRejectedByDirectoryValidation() {
    FollowupRuleRepository repository = Mockito.mock(FollowupRuleRepository.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    TagSelectionValidationResult rejected = Mockito.mock(TagSelectionValidationResult.class);
    when(rejected.accepted()).thenReturn(false);
    when(rejected.message()).thenReturn("标签分类已停用");
    when(repository.nameExists("tag rule", null)).thenReturn(false);
    when(validator.validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L)), any()))
        .thenReturn(rejected);
    RuleAdminService service = service(repository, validator);

    RuleRequest request = new RuleRequest(
        "tag rule",
        "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51],\"match\":\"ANY\"}]}",
        ActionType.ALERT,
        "{}",
        10,
        true);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(FollowupException.class)
        .extracting(ex -> ((FollowupException) ex).getErrorCode())
        .isEqualTo(FollowupErrorCodes.BAD_REQUEST);
    verify(repository, never()).create(any());
  }

  @Test
  void legacyTextTagChangeDoesNotRequireFormalDirectoryTarget() {
    FollowupRuleRepository repository = Mockito.mock(FollowupRuleRepository.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    when(repository.nameExists("legacy", null)).thenReturn(false);
    when(repository.create(any())).thenReturn(10L);
    when(repository.findById(10L)).thenReturn(java.util.Optional.of(new com.privateflow.modules.followup.FollowupRule(
        10L, "legacy", "{}", ActionType.TAG_CHANGE, "{}", 10, true, false, null, null)));
    RuleAdminService service = service(repository, validator);

    service.create(new RuleRequest(
        "legacy",
        "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"leadType\",\"op\":\"EQ\",\"value\":\"XIAN_SUO\"}]}",
        ActionType.TAG_CHANGE,
        "{\"tagName\":\"旧文本建议\"}",
        10,
        true));

    verify(validator, never()).validateIds(any(), anyLong(), any(), any());
  }

  @Test
  void formalTagChangeTargetUsesFollowupDirectoryValidation() {
    FollowupRuleRepository repository = Mockito.mock(FollowupRuleRepository.class);
    TagSelectionValidator validator = Mockito.mock(TagSelectionValidator.class);
    TagSelectionValidationResult accepted = Mockito.mock(TagSelectionValidationResult.class);
    when(accepted.accepted()).thenReturn(true);
    when(repository.nameExists("formal", null)).thenReturn(false);
    when(repository.create(any())).thenReturn(11L);
    when(repository.findById(11L)).thenReturn(java.util.Optional.of(new com.privateflow.modules.followup.FollowupRule(
        11L, "formal", "{}", ActionType.TAG_CHANGE, "{}", 10, true, false, null, null)));
    when(validator.validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L)), any()))
        .thenReturn(accepted);
    RuleAdminService service = service(repository, validator);

    service.create(new RuleRequest(
        "formal",
        "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"leadType\",\"op\":\"EQ\",\"value\":\"XIAN_SUO\"}]}",
        ActionType.TAG_CHANGE,
        "{\"tagCategoryId\":50,\"tagCategoryKey\":\"intent_level\",\"tagValueId\":51,\"tagValue\":\"HIGH\",\"tagName\":\"高意向\"}",
        10,
        true));

    verify(validator).validateIds(eq(TagCandidatePurpose.FOLLOWUP_RULE), eq(50L), eq(List.of(51L)), any());
  }

  private RuleAdminService service(FollowupRuleRepository repository, TagSelectionValidator validator) {
    ObjectMapper mapper = new ObjectMapper();
    return new RuleAdminService(
        repository,
        mapper,
        Mockito.mock(RuleLoader.class),
        Mockito.mock(AuditLogger.class),
        new ConditionEvaluator(mapper),
        validator);
  }
}
