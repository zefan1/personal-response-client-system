package com.privateflow.modules.tablewrite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.sync.SheetSource;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WecomSmartSheetDatasourceInitializerTest {

  @Test
  void onlyAddsMissingDefaultMappingsWithoutChangingDatasources() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    org.mockito.Mockito.when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    WecomSmartSheetDatasourceInitializer initializer = new WecomSmartSheetDatasourceInitializer(
        configured(), jdbcTemplate, publisher);

    initializer.run(null);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(13)).update(sql.capture(), arguments.capture());
    assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value)
        .contains("INSERT INTO datasource_field_mappings", "WHERE NOT EXISTS")
        .doesNotContain("UPDATE datasources", "is_enabled = 0"));
    assertThat(arguments.getAllValues())
        .extracting(values -> List.of(values).toString())
        .containsExactlyInAnyOrder(
            "[sheet-1, 联系方式, phone, sheet-1, phone]",
            "[sheet-1, 备注称呼, nickname, sheet-1, nickname]",
            "[sheet-1, 客资类型, leadType, sheet-1, leadType]",
            "[sheet-1, 客户阶段, customerStage, sheet-1, customerStage]",
            "[sheet-1, 客户关注点, bodyConcerns, sheet-1, bodyConcerns]",
            "[sheet-1, 客户B档案, customerProfileSummary, sheet-1, customerProfileSummary]",
            "[sheet-1, 跟进记录, followupNotes, sheet-1, followupNotes]",
            "[sheet-1, 备注, internalNote, sheet-1, internalNote]",
            "[sheet-1, 第一次追踪捕捉, firstTrackingCapture, sheet-1, firstTrackingCapture]",
            "[sheet-1, 第二次追踪捕捉, secondTrackingCapture, sheet-1, secondTrackingCapture]",
            "[sheet-1, 第三次追踪捕捉, thirdTrackingCapture, sheet-1, thirdTrackingCapture]",
            "[sheet-1, 下次跟进方向, nextFollowupDir, sheet-1, nextFollowupDir]",
            "[sheet-1, 下次跟进时间, nextFollowupAt, sheet-1, nextFollowupAt]");
    verify(publisher).publishEvent(new ConfigChangedEvent("datasource.field_mappings"));
  }

  @Test
  void leavesTheDatabaseUntouchedWhenSmartSheetConfigurationIsIncomplete() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    WecomSmartSheetConfig incomplete = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "", "", "", "", "", "", "", ZoneId.of("Asia/Shanghai"));

    new WecomSmartSheetDatasourceInitializer(incomplete, jdbcTemplate, publisher).run(null);

    verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    verify(jdbcTemplate, never()).update(anyString());
    verify(publisher, never()).publishEvent(any());
  }

  @Test
  void preservesExistingDatasourceStatesAndIgnoresLegacyRows() throws Exception {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:wecom_datasource_initializer;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS datasource_field_mappings");
    jdbcTemplate.execute("DROP TABLE IF EXISTS datasources");
    jdbcTemplate.execute("""
        CREATE TABLE datasources (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(100) NOT NULL UNIQUE,
          sheet_id VARCHAR(100) NOT NULL,
          source_table VARCHAR(100) NOT NULL,
          description VARCHAR(255),
          is_enabled TINYINT NOT NULL DEFAULT 1,
          created_by VARCHAR(100) NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE datasource_field_mappings (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          source_table VARCHAR(100) NOT NULL,
          source_field VARCHAR(200) NOT NULL,
          target_field VARCHAR(100) NOT NULL,
          transform_rule VARCHAR(255),
          is_enabled TINYINT NOT NULL DEFAULT 1,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE (source_table, target_field)
        )
        """);
    jdbcTemplate.update("""
        INSERT INTO datasources (name, sheet_id, source_table, description, is_enabled, created_by)
        VALUES ('客户主表', 'doc-1', 'sheet-1', 'SYSTEM_MANAGED_SMART_SHEET:PRIMARY', 0, 'SYSTEM'),
               ('分配表', 'assignment-doc', 'ASSIGNMENT:sheet-2', 'SYSTEM_MANAGED_SMART_SHEET:ASSIGNMENT', 1, 'SYSTEM'),
               ('PrivateDomainAssistant-API', 'old-document', 'old-sheet', 'old target', 1, 'SYSTEM')
        """);
    WecomSmartSheetConfig config = configured();

    new WecomSmartSheetDatasourceInitializer(config, jdbcTemplate, mock(ApplicationEventPublisher.class))
        .run(null);

    DatasourceAdminRepository repository = new DatasourceAdminRepository(jdbcTemplate);
    assertThat(repository.enabledSources())
        .containsExactly(new SheetSource(2L, "assignment-doc", "ASSIGNMENT:sheet-2"));
    assertThat(jdbcTemplate.queryForList("SELECT name, is_enabled FROM datasources ORDER BY id"))
        .extracting(row -> row.get("name") + "=" + row.get("is_enabled"))
        .containsExactly(
            "客户主表=0",
            "分配表=1",
            "PrivateDomainAssistant-API=1");
    assertThat(jdbcTemplate.queryForList("""
        SELECT source_field, target_field
        FROM datasource_field_mappings
        WHERE source_table = 'sheet-1' AND is_enabled = 1
        ORDER BY target_field
        """))
        .extracting(row -> row.get("source_field") + "=" + row.get("target_field"))
        .containsExactly(
            "客户关注点=bodyConcerns",
            "客户B档案=customerProfileSummary",
            "客户阶段=customerStage",
            "第一次追踪捕捉=firstTrackingCapture",
            "跟进记录=followupNotes",
            "备注=internalNote",
            "客资类型=leadType",
            "下次跟进时间=nextFollowupAt",
            "下次跟进方向=nextFollowupDir",
            "备注称呼=nickname",
            "联系方式=phone",
            "第二次追踪捕捉=secondTrackingCapture",
            "第三次追踪捕捉=thirdTrackingCapture");
  }

  private static WecomSmartSheetConfig configured() {
    return new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "corp", "secret", "doc-1", "sheet-1", "view-1",
        "sheet-1", "联系方式", ZoneId.of("Asia/Shanghai"));
  }
}
