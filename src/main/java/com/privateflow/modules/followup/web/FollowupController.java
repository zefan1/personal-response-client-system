package com.privateflow.modules.followup.web;

import com.privateflow.modules.followup.ActionType;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import com.privateflow.modules.match.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FollowupController {

  private final FollowupTodayService todayService;
  private final RuleAdminService ruleAdminService;
  private final SystemConfigRepository systemConfigRepository;
  private final ObjectMapper objectMapper;
  private final QuickSearchService quickSearchService;

  public FollowupController(FollowupTodayService todayService, RuleAdminService ruleAdminService) {
    this(todayService, ruleAdminService, null, null, null);
  }

  @Autowired
  public FollowupController(
      FollowupTodayService todayService,
      RuleAdminService ruleAdminService,
      SystemConfigRepository systemConfigRepository,
      ObjectMapper objectMapper,
      QuickSearchService quickSearchService) {
    this.todayService = todayService;
    this.ruleAdminService = ruleAdminService;
    this.systemConfigRepository = systemConfigRepository;
    this.objectMapper = objectMapper;
    this.quickSearchService = quickSearchService;
  }

  @GetMapping("/api/v1/followups/today")
  public ApiResponse<FollowupTodayResponse> today(@RequestParam(value = "keeperId", required = false) String keeperId) {
    return ApiResponse.ok(todayService.today(keeperId));
  }

  @GetMapping("/api/v1/followups/friend-request-templates")
  public ApiResponse<FriendRequestTemplatesResponse> friendRequestTemplates() {
    List<FriendRequestTemplate> templates = new ArrayList<>();
    String raw = systemConfigRepository == null
        ? "[\"你好，我是负责跟进你的顾问，方便通过一下好友申请吗？\"]"
        : systemConfigRepository.findValue("followup.friend_request_templates_json")
            .orElse(null);
    boolean fallbackRequired = raw == null;
    try {
      JsonNode node = objectMapper == null ? null : objectMapper.readTree(raw);
      if (node != null && node.isArray()) {
        fallbackRequired = false;
        Map<Long, QuickSearchItem> quickSearchTemplates = quickSearchService == null
            ? Map.of()
            : quickSearchService.listEnabledItems().stream()
                .filter(item -> item.contentType() == ContentType.TEMPLATE)
                .collect(Collectors.toMap(QuickSearchItem::id, Function.identity(), (left, right) -> left));
        int index = 1;
        for (JsonNode item : node) {
          if (!item.isTextual() && !item.path("enabled").asBoolean(true)) {
            continue;
          }
          QuickSearchItem source = item.hasNonNull("quickSearchItemId")
              ? quickSearchTemplates.get(item.path("quickSearchItemId").asLong())
              : null;
          String text = source != null ? source.content() : item.isTextual() ? item.asText() : item.path("text").asText("");
          if (text.isBlank() || (source == null && item.hasNonNull("quickSearchItemId"))) {
            continue;
          }
          String id = source != null
              ? "quick-search-" + source.id()
              : item.isTextual() ? String.valueOf(index) : item.path("id").asText(String.valueOf(index));
          String name = source != null
              ? source.title()
              : item.isTextual() ? "话术 " + index : item.path("name").asText("话术 " + index);
          templates.add(new FriendRequestTemplate(id, name, text.trim(), true));
          index++;
        }
      }
    } catch (Exception ignored) {
      fallbackRequired = true;
    }
    if (fallbackRequired) {
      // Keep the built-in phrase only for an uninitialized or malformed configuration.
      templates.add(new FriendRequestTemplate("default", "默认话术", "你好，我是负责跟进你的顾问，方便通过一下好友申请吗？", true));
    }
    return ApiResponse.ok(new FriendRequestTemplatesResponse(templates));
  }

  @GetMapping("/admin/api/v1/rules")
  public ApiResponse<RulePage> rules(
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "actionType", required = false) ActionType actionType,
      @RequestParam(value = "enabled", required = false) Boolean enabled) {
    return ApiResponse.ok(ruleAdminService.search(new RuleSearchCriteria(page, size, keyword, actionType, enabled)));
  }

  @PostMapping("/admin/api/v1/rules")
  public ApiResponse<FollowupRule> create(@RequestBody RuleRequest request) {
    return ApiResponse.ok(ruleAdminService.create(request));
  }

  @PutMapping("/admin/api/v1/rules/{id}")
  public ApiResponse<FollowupRule> update(@PathVariable("id") long id, @RequestBody RuleRequest request) {
    return ApiResponse.ok(ruleAdminService.update(id, request));
  }

  @DeleteMapping("/admin/api/v1/rules/{id}")
  public ApiResponse<Void> delete(@PathVariable("id") long id) {
    ruleAdminService.delete(id);
    return ApiResponse.ok(null);
  }

  @PutMapping("/admin/api/v1/rules/{id}/toggle")
  public ApiResponse<FollowupRule> toggle(@PathVariable("id") long id, @RequestBody ToggleRequest request) {
    return ApiResponse.ok(ruleAdminService.toggle(id, request.enabled()));
  }

  @ExceptionHandler(FollowupException.class)
  public ResponseEntity<ApiResponse<Void>> handleFollowup(FollowupException ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (FollowupErrorCodes.BAD_REQUEST.equals(ex.getErrorCode())
        || FollowupErrorCodes.CONDITION_PARSE_FAILED.equals(ex.getErrorCode())) {
      status = HttpStatus.BAD_REQUEST;
    } else if (FollowupErrorCodes.FORBIDDEN.equals(ex.getErrorCode())) {
      status = HttpStatus.FORBIDDEN;
    }
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }

  public record ToggleRequest(boolean enabled) {
  }

  public record FriendRequestTemplatesResponse(List<FriendRequestTemplate> templates) {
  }

  public record FriendRequestTemplate(String id, String name, String text, boolean enabled) {
  }
}
