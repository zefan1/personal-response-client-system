package com.privateflow.modules.tablewrite.config;

import com.privateflow.common.events.ConfigChangedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Installs the configured API-owned Smart Sheet as the application's business datasource. */
@Component
public final class WecomSmartSheetDatasourceInitializer implements ApplicationRunner {

  private static final String DATASOURCE_NAME = "PrivateDomainAssistant-API";
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

    jdbcTemplate.update("""
        UPDATE datasource_field_mappings
        SET is_enabled = 0, updated_at = NOW()
        WHERE source_table = ?
          AND target_field IN (
            'phone', 'nickname', 'leadType', 'customerStage',
            'bodyConcerns', 'customerProfileSummary', 'followupNotes', 'internalNote',
            'firstTrackingCapture', 'secondTrackingCapture', 'thirdTrackingCapture',
            'nextFollowupDir', 'nextFollowupAt', 'lastFollowupAt'
          )
        """, config.sourceTable());

    jdbcTemplate.update("""
        UPDATE datasources
        SET is_enabled = 0, updated_at = NOW()
        WHERE is_enabled = 1
        """);

    jdbcTemplate.update("""
        INSERT INTO datasources (name, sheet_id, source_table, description, is_enabled, created_by)
        VALUES (?, ?, ?, ?, 1, ?)
        ON DUPLICATE KEY UPDATE
          sheet_id = VALUES(sheet_id),
          source_table = VALUES(source_table),
          description = VALUES(description),
          is_enabled = 1,
          updated_at = NOW()
        """, DATASOURCE_NAME, config.documentId(), config.sourceTable(),
        "Enterprise WeCom Smart Sheet API datasource", "SYSTEM");

    for (Map.Entry<String, String> mapping : BUSINESS_MAPPINGS.entrySet()) {
      String sourceField = "phone".equals(mapping.getValue())
          ? config.uniqueFieldTitle()
          : mapping.getKey();
      jdbcTemplate.update("""
          INSERT INTO datasource_field_mappings (source_table, source_field, target_field, transform_rule, is_enabled)
          VALUES (?, ?, ?, NULL, 1)
          ON DUPLICATE KEY UPDATE is_enabled = 1, transform_rule = NULL, updated_at = NOW()
          """, config.sourceTable(), sourceField, mapping.getValue());
    }
    eventPublisher.publishEvent(new ConfigChangedEvent("datasource.field_mappings"));
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
