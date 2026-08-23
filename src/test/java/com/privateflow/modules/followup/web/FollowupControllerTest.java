package com.privateflow.modules.followup.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import com.privateflow.modules.followup.FollowupItem;
import com.privateflow.modules.followup.FollowupRule;
import com.privateflow.modules.followup.FollowupTodayResponse;
import com.privateflow.modules.followup.RulePage;
import com.privateflow.modules.followup.RuleRequest;
import com.privateflow.modules.followup.RuleSearchCriteria;
import com.privateflow.modules.followup.service.FollowupTodayService;
import com.privateflow.modules.followup.service.RuleAdminService;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.quicksearch.ContentType;
import com.privateflow.modules.quicksearch.QuickSearchItem;
import com.privateflow.modules.quicksearch.QuickSearchService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FollowupControllerTest {

  private FollowupTodayService todayService;
  private RuleAdminService ruleAdminService;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    todayService = org.mockito.Mockito.mock(FollowupTodayService.class);
    ruleAdminService = org.mockito.Mockito.mock(RuleAdminService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new FollowupController(todayService, ruleAdminService))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void todayBindsKeeperIdAndReturnsItems() throws Exception {
    when(todayService.today("keeper-1")).thenReturn(new FollowupTodayResponse("keeper-1", 1, List.of(followupItem())));

    mockMvc.perform(get("/api/v1/followups/today").param("keeperId", "keeper-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.keeperId").value("keeper-1"))
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].phone").value("138****0000"))
        .andExpect(jsonPath("$.data.items[0].phoneFull").value("13800000000"));

    verify(todayService).today("keeper-1");
  }

  @Test
  void rulesBindsSearchCriteria() throws Exception {
    when(ruleAdminService.search(any())).thenReturn(new RulePage(2, 5, 1, List.of(rule(3L, true))));

    mockMvc.perform(get("/admin/api/v1/rules")
            .param("page", "2")
            .param("size", "5")
            .param("keyword", "overdue")
            .param("actionType", "ALERT")
            .param("enabled", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.items[0].actionType").value("ALERT"));

    ArgumentCaptor<RuleSearchCriteria> captor = ArgumentCaptor.forClass(RuleSearchCriteria.class);
    verify(ruleAdminService).search(captor.capture());
    RuleSearchCriteria criteria = captor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals(2, criteria.page());
    org.junit.jupiter.api.Assertions.assertEquals(5, criteria.size());
    org.junit.jupiter.api.Assertions.assertEquals("overdue", criteria.keyword());
    org.junit.jupiter.api.Assertions.assertEquals(ActionType.ALERT, criteria.actionType());
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, criteria.enabled());
  }

  @Test
  void createUpdateDeleteAndToggleDelegateToService() throws Exception {
    RuleRequest request = new RuleRequest("Overdue alert", "{\"overdueHours\":24}", ActionType.ALERT, "{\"level\":\"HIGH\"}", 10, true);
    when(ruleAdminService.create(any())).thenReturn(rule(4L, true));
    when(ruleAdminService.update(eq(4L), any())).thenReturn(rule(4L, true));
    when(ruleAdminService.toggle(4L, false)).thenReturn(rule(4L, false));
    doNothing().when(ruleAdminService).delete(4L);

    mockMvc.perform(post("/admin/api/v1/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(4))
        .andExpect(jsonPath("$.data.name").value("Overdue alert"));

    mockMvc.perform(put("/admin/api/v1/rules/4")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actionType").value("ALERT"));

    mockMvc.perform(put("/admin/api/v1/rules/4/toggle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));

    mockMvc.perform(delete("/admin/api/v1/rules/4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(ruleAdminService).create(any());
    verify(ruleAdminService).update(eq(4L), any());
    verify(ruleAdminService).toggle(4L, false);
    verify(ruleAdminService).delete(4L);
  }

  @Test
  void invalidActionTypeQueryReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/admin/api/v1/rules").param("actionType", "BROKEN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void conditionParseFailureMapsToBadRequest() throws Exception {
    when(ruleAdminService.create(any()))
        .thenThrow(new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "condition invalid"));

    mockMvc.perform(post("/admin/api/v1/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"bad\",\"conditionJson\":\"not-json\",\"actionType\":\"ALERT\",\"actionConfig\":\"{}\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(FollowupErrorCodes.CONDITION_PARSE_FAILED));
  }

  @Test
  void friendRequestTemplatesResolveSelectedQuickSearchItemsAndSkipDisabledEntries() throws Exception {
    SystemConfigRepository configRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
    QuickSearchService quickSearchService = org.mockito.Mockito.mock(QuickSearchService.class);
    when(configRepository.findValue("followup.friend_request_templates_json"))
        .thenReturn(Optional.of("[{\"quickSearchItemId\":7,\"enabled\":true},{\"quickSearchItemId\":8,\"enabled\":false}]"));
    when(quickSearchService.listEnabledItems()).thenReturn(List.of(
        new QuickSearchItem(7L, ContentType.TEMPLATE, null, "GENERAL", "加微开场", "ADD01", "你好，方便通过好友申请吗？", null, 1, true, LocalDateTime.now()),
        new QuickSearchItem(8L, ContentType.TEMPLATE, null, "GENERAL", "停用话术", "ADD02", "不应显示", null, 2, true, LocalDateTime.now())));

    mockMvc = MockMvcBuilders
        .standaloneSetup(new FollowupController(todayService, ruleAdminService, configRepository, objectMapper, quickSearchService))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();

    mockMvc.perform(get("/api/v1/followups/friend-request-templates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.templates.length()").value(1))
        .andExpect(jsonPath("$.data.templates[0].id").value("quick-search-7"))
        .andExpect(jsonPath("$.data.templates[0].name").value("加微开场"))
        .andExpect(jsonPath("$.data.templates[0].text").value("你好，方便通过好友申请吗？"));
  }

  @Test
  void forbiddenFailureMapsToForbidden() throws Exception {
    when(ruleAdminService.search(any()))
        .thenThrow(new FollowupException(FollowupErrorCodes.FORBIDDEN, "permission denied"));

    mockMvc.perform(get("/admin/api/v1/rules"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(FollowupErrorCodes.FORBIDDEN));
  }

  private FollowupRule rule(long id, boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new FollowupRule(id, "Overdue alert", "{\"overdueHours\":24}", ActionType.ALERT, "{\"level\":\"HIGH\"}", 10, enabled, false, now, now);
  }

  private FollowupItem followupItem() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new FollowupItem(
        "138****0000",
        "13800000000",
        "Alice",
        "TUAN_GOU",
        now.minusDays(2),
        now.plusHours(2),
        "FOLLOW",
        LocalDate.of(2026, 7, 4),
        "Store A",
        "sheet-a",
        null,
        26L,
        null,
        null,
        now);
  }
}
