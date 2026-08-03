package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LlmFollowupAnalysisServiceTest {

  private LlmService llmService;
  private SystemConfigRepository configRepository;
  private LlmFollowupAnalysisService service;

  @BeforeEach
  void setUp() {
    llmService = Mockito.mock(LlmService.class);
    configRepository = Mockito.mock(SystemConfigRepository.class);
    when(configRepository.findValue("llm.summary.enabled")).thenReturn(Optional.of("true"));
    when(configRepository.findValue("llm.summary.system_prompt")).thenReturn(Optional.empty());
    when(configRepository.findValue("llm.summary.temperature")).thenReturn(Optional.of("0.2"));
    when(configRepository.findValue("llm.summary.max_tokens")).thenReturn(Optional.of("1200"));
    service = new LlmFollowupAnalysisService(llmService, configRepository, new ObjectMapper());
  }

  @Test
  void analyzesAllFollowupFieldsInOneCallWithTheExistingCustomerProfile() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            {
              "internal_note":"客户重视安全感，需先解释评估价值",
              "body_concerns":"腹直肌分离、腰痛",
              "customer_profile_summary":"产后6个月，顺产，母乳中，关注腹直肌和腰痛",
              "followup_record":"2026-08-01：客户询问评估流程，约定周一联系",
              "customer_stage":"高意向",
              "next_followup_direction":"说明评估流程并确认到店时间",
              "next_followup_at":"2026-08-03T10:00:00",
              "next_followup_time_explicit":true,
              "tracking_capture":"客户明确周一上午可以联系"
            }
            """, "gpt", "OPENAI_COMPATIBLE", 80));

    Optional<FollowupAnalysisPayload> result = service.tryAnalyze(input());

    assertThat(result).contains(new FollowupAnalysisPayload(
        "客户重视安全感，需先解释评估价值",
        "腹直肌分离、腰痛",
        "产后6个月，顺产，母乳中，关注腹直肌和腰痛",
        "2026-08-01：客户询问评估流程，约定周一联系",
        "高意向",
        "说明评估流程并确认到店时间",
        "2026-08-03T10:00",
        "客户明确周一上午可以联系"));
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().userPrompt())
        .contains("\"bodyConcerns\":\"腹直肌分离\"")
        .contains("\"internalNote\":\"旧的内部提醒\"")
        .contains("\"customerProfileSummary\":\"旧的客户B档案\"")
        .contains("\"firstTrackingCapture\":\"首次明确关注恢复周期\"")
        .contains("\"role\":\"customer\"")
        .contains("\"role\":\"employee\"")
        .contains("\"customerMessages\":[\"周一上午可以联系我\"]")
        .doesNotContain("18800001111")
        .contains("1111");
    assertThat(captor.getValue().systemPrompt())
        .contains("客户原话是新增客户事实的唯一证据")
        .contains("body_concerns 必须填写")
        .contains("customer_profile_summary 必须更新")
        .contains("tracking_capture 必须填写")
        .contains("不得把肚子大推断为腹直肌分离");
  }

  @Test
  void rejectsInventedNextFollowupTimeWhenTheConversationDidNotStateItExplicitly() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("""
            {
              "followup_record":"客户表示后续再联系",
              "next_followup_direction":"继续确认需求",
              "next_followup_at":"2026-08-03T10:00:00",
              "next_followup_time_explicit":false
            }
            """, "gpt", "OPENAI_COMPATIBLE", 40));

    FollowupAnalysisPayload result = service.tryAnalyze(input()).orElseThrow();

    assertThat(result.nextFollowupAt()).isNull();
    assertThat(result.nextFollowupDirection()).isEqualTo("继续确认需求");
  }

  @Test
  void repairsAMissingBodyConcernUsingOnlyExactCustomerQuotes() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(
            LlmResponse.ok("""
                {
                  "internal_note":"继续了解腰痛场景和腹部困扰",
                  "body_concerns":null,
                  "customer_profile_summary":"宝宝3个月，主诉腰痛和肚子大",
                  "followup_record":"客户确认宝宝3个月，表示一直腰痛、肚子很大",
                  "customer_stage":"破冰阶段",
                  "next_followup_direction":"了解腰痛位置和日常活动影响",
                  "next_followup_at":null,
                  "next_followup_time_explicit":false,
                  "tracking_capture":"客户首次明确身体困扰"
                }
                """, "gpt", "OPENAI_COMPATIBLE", 80),
            LlmResponse.ok("""
                {
                  "has_explicit_body_concern":true,
                  "evidence_quotes":["腰痛","肚子也很大"]
                }
                """, "gpt", "OPENAI_COMPATIBLE", 30));

    FollowupAnalysisPayload result = service.tryAnalyze(xiaoqianInput()).orElseThrow();

    assertThat(result.bodyConcerns()).isEqualTo("腰痛、肚子也很大");
    verify(llmService, times(2))
        .generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any());
  }

  @Test
  void retriesWhenTheEvidenceCheckMissesAnObviousFirstPersonSymptom() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(
            LlmResponse.ok("""
                {
                  "customer_profile_summary":"宝宝3个月，主诉腰痛和肚子大",
                  "followup_record":"客户确认宝宝3个月，表示一直腰痛、肚子很大",
                  "tracking_capture":"客户首次明确身体困扰"
                }
                """, "gpt", "OPENAI_COMPATIBLE", 60),
            LlmResponse.ok("""
                {
                  "has_explicit_body_concern":false,
                  "evidence_quotes":[]
                }
                """, "gpt", "OPENAI_COMPATIBLE", 20));

    assertThat(service.tryAnalyze(xiaoqianInput())).isEmpty();
  }

  @Test
  void returnsEmptyWhenTheAnalysisCannotBeGeneratedOrParsed() {
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.failed(LlmErrorCodes.CONFIG_MISSING, "missing", "", "OPENAI_COMPATIBLE", 1));
    assertThat(service.tryAnalyze(input())).isEmpty();

    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{}", "gpt", "OPENAI_COMPATIBLE", 1));
    assertThat(service.tryAnalyze(input())).isEmpty();
  }

  @Test
  void neverUsesTheLegacySummaryOnlyPromptForStructuredAnalysis() {
    when(configRepository.findValue("llm.summary.system_prompt"))
        .thenReturn(Optional.of("Return only {summary: string}."));
    when(llmService.generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), any()))
        .thenReturn(LlmResponse.ok("{\"followup_record\":\"客户明确周一联系\"}",
            "gpt", "OPENAI_COMPATIBLE", 20));

    service.tryAnalyze(input());

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmService).generate(eq(LlmScene.SUMMARY), eq("TUAN_GOU"), eq("keeper"), any(), captor.capture());
    assertThat(captor.getValue().systemPrompt())
        .contains("internal_note", "customer_profile_summary", "tracking_capture")
        .doesNotContain("Return only {summary: string}.");
  }

  private LlmFollowupAnalysisInput input() {
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setNickname("小倩");
    customer.setLeadType("TUAN_GOU");
    customer.setBodyConcerns("腹直肌分离");
    customer.setFollowupNotes("2026-07-31：客户首次咨询");
    customer.setInternalNote("旧的内部提醒");
    customer.setCustomerProfileSummary("旧的客户B档案");
    customer.setFirstTrackingCapture("首次明确关注恢复周期");
    return new LlmFollowupAnalysisInput(
        customer,
        List.of(
            new CustomerMessageSentEvent.ChatMessage("client", "周一上午可以联系我", "12:00"),
            new CustomerMessageSentEvent.ChatMessage("assistant", "好的，周一联系您", "12:01")),
        "好的，周一联系您",
        "NEXT_STEP",
        "keeper");
  }

  private LlmFollowupAnalysisInput xiaoqianInput() {
    Customer customer = new Customer();
    customer.setPhone("15622907970");
    customer.setNickname("小倩");
    customer.setLeadType("TUAN_GOU");
    return new LlmFollowupAnalysisInput(
        customer,
        List.of(
            new CustomerMessageSentEvent.ChatMessage("client", "你们产后修复有什么项目", "10:00"),
            new CustomerMessageSentEvent.ChatMessage("assistant", "宝宝多大了？最想改善哪方面？", "10:01"),
            new CustomerMessageSentEvent.ChatMessage("client", "现在宝宝3个月了，我一直觉得腰痛，肚子也很大", "10:02")),
        "可以先了解腰痛的位置和日常活动影响",
        "NEXT_STEP",
        "keeper");
  }
}
