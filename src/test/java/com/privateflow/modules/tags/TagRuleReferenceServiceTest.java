package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TagRuleReferenceServiceTest {

  private JdbcTemplate jdbcTemplate;
  private ObjectMapper objectMapper;
  private TagRuleReferenceService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:tag_rule_references;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS followup_rules");
    jdbcTemplate.execute("""
        CREATE TABLE followup_rules (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(100) NOT NULL,
          condition_json VARCHAR(2000) NOT NULL,
          action_config VARCHAR(2000) NOT NULL,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    objectMapper = new ObjectMapper();
    service = new TagRuleReferenceService(jdbcTemplate, objectMapper);
  }

  @Test
  void countsAndStructurallyRewritesCategoryAndValueReferences() throws Exception {
    jdbcTemplate.update("""
        INSERT INTO followup_rules (name, condition_json, action_config)
        VALUES (?, ?, ?)
        """,
        "高意向跟进",
        "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"intentLevel\",\"op\":\"EQ\",\"value\":\"HIGH\",\"tagValueId\":2,\"categoryId\":1}]}",
        "{\"tagName\":\"高意向\",\"tagCategoryKey\":\"intent_level\"}");
    TagCategory sourceCategory = category(1L, "intent_level", "intentLevel");
    TagCategory targetCategory = category(4L, "customer_stage", "customerStage");
    TagValue source = value(2L, 1L, "intent_level", "HIGH", "高意向");
    TagValue target = value(8L, 4L, "customer_stage", "PRIORITY", "优先跟进");
    sourceCategory = sourceCategory.withValues(List.of(source));

    TagRuleReferenceService.ReferenceCounts counts = service.countReferences(
        List.of(sourceCategory),
        List.of(source));
    assertThat(counts.category(1L)).isEqualTo(1);
    assertThat(counts.value(2L)).isEqualTo(1);

    int changed = service.rewriteCategory(sourceCategory, targetCategory, java.util.Map.of(2L, target));
    assertThat(changed).isEqualTo(1);
    JsonNode condition = objectMapper.readTree(jdbcTemplate.queryForObject(
        "SELECT condition_json FROM followup_rules WHERE id = 1",
        String.class));
    JsonNode action = objectMapper.readTree(jdbcTemplate.queryForObject(
        "SELECT action_config FROM followup_rules WHERE id = 1",
        String.class));
    JsonNode item = condition.path("conditions").path(0);
    assertThat(item.path("field").asText()).isEqualTo("customerStage");
    assertThat(item.path("value").asText()).isEqualTo("PRIORITY");
    assertThat(item.path("tagValueId").asLong()).isEqualTo(8L);
    assertThat(item.path("categoryId").asLong()).isEqualTo(4L);
    assertThat(action.path("tagName").asText()).isEqualTo("优先跟进");
    assertThat(action.path("tagCategoryKey").asText()).isEqualTo("customer_stage");
  }

  private TagCategory category(long id, String key, String boundField) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 12, 0);
    return new TagCategory(id, key, key, boundField, false, true, 1, List.of(), now, now);
  }

  private TagValue value(long id, long categoryId, String categoryKey, String code, String displayName) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 12, 0);
    return new TagValue(id, categoryId, categoryKey, code, displayName, true, 1, now, now);
  }
}
