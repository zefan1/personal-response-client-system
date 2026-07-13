package com.privateflow.modules.followup.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import com.privateflow.modules.followup.RuleRequest;
import com.privateflow.modules.followup.infra.FollowupRuleRepository;
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
        new ConditionEvaluator(new ObjectMapper()));

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
}
