package com.privateflow.modules.followup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

  private ObjectMapper mapper;
  private ConditionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    evaluator = new ConditionEvaluator(mapper);
  }

  @Test
  void tagMatchAnyAndAllUseCurrentContext() {
    Customer customer = customer();
    FollowupTagContext tags = FollowupTagContext.of(Map.of(50L, Set.of(51L, 53L)));

    assertThat(evaluator.matches(customer,
        "{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51,52],\"match\":\"ANY\"}", tags))
        .isTrue();
    assertThat(evaluator.matches(customer,
        "{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":50,\"valueIds\":[51,52],\"match\":\"ALL\"}", tags))
        .isFalse();
  }

  @Test
  void invalidTagLeafIsRejected() throws Exception {
    JsonNode node = mapper.readTree(
        "{\"field\":\"tag\",\"op\":\"MATCH\",\"categoryId\":0,\"valueIds\":[],\"match\":\"XOR\"}");

    assertThatThrownBy(() -> evaluator.validateDefinition(node))
        .isInstanceOf(FollowupException.class)
        .extracting(ex -> ((FollowupException) ex).getErrorCode())
        .isEqualTo(FollowupErrorCodes.CONDITION_PARSE_FAILED);
  }

  @Test
  void legacyLeafStillMatchesWithoutTagContext() {
    assertThat(evaluator.matches(customer(),
        "{\"field\":\"leadType\",\"op\":\"EQ\",\"value\":\"XIAN_SUO\"}"))
        .isTrue();
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setLeadType("XIAN_SUO");
    return customer;
  }
}
