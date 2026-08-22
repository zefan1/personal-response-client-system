package com.privateflow.modules.tablewrite.config;

import com.privateflow.common.events.ConfigChangedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds missing default mappings for the configured customer-master Smart Sheet.
 *
 * <p>Datasource registration and enablement belong to the administrator-managed three-table
 * configuration. This startup hook must never change an operator's enablement choice.
 */
@Component
public final class WecomSmartSheetDatasourceInitializer implements ApplicationRunner {

  private static final Map<String, String> BUSINESS_MAPPINGS = mappings();

  private final WecomSmartSheetConfig config;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher eventPublisher;

  public WecomSmartSheetDatasourceInitializer(
      WecomSmartSheetConfig config,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher eventPublisher) {
    this.config = config;
    this.jdbcTemplate = jdbcTemplate;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      config.requireConfigured();
    } catch (IllegalStateException incomplete) {
      return;
    }

    int insertedMappings = 0;
    for (Map.Entry<String, String> mapping : BUSINESS_MAPPINGS.entrySet()) {
      String sourceField = "phone".equals(mapping.getValue())
          ? config.uniqueFieldTitle()
          : mapping.getKey();
      insertedMappings += jdbcTemplate.update("""
          INSERT INTO datasource_field_mappings (
              source_table, source_field, target_field, transform_rule, is_enabled)
          SELECT ?, ?, ?, NULL, 1
          WHERE NOT EXISTS (
              SELECT 1
              FROM datasource_field_mappings
              WHERE source_table = ? AND target_field = ?
          )
          """, config.sourceTable(), sourceField, mapping.getValue(), config.sourceTable(), mapping.getValue());
    }
    if (insertedMappings > 0) {
      eventPublisher.publishEvent(new ConfigChangedEvent("datasource.field_mappings"));
    }
  }

  private static Map<String, String> mappings() {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("联系方式", "phone");
    result.put("备注称呼", "nickname");
    result.put("客资类型", "leadType");
    result.put("客户阶段", "customerStage");
    result.put("客户关注点", "bodyConcerns");
    result.put("客户B档案", "customerProfileSummary");
    result.put("跟进记录", "followupNotes");
    result.put("备注", "internalNote");
    result.put("第一次追踪捕捉", "firstTrackingCapture");
    result.put("第二次追踪捕捉", "secondTrackingCapture");
    result.put("第三次追踪捕捉", "thirdTrackingCapture");
    result.put("下次跟进方向", "nextFollowupDir");
    result.put("下次跟进时间", "nextFollowupAt");
    return Map.copyOf(result);
  }
}
