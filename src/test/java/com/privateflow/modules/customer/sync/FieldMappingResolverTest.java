package com.privateflow.modules.customer.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;

class FieldMappingResolverTest {

  private JdbcTemplate jdbcTemplate;
  private DataSource dataSource;

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:field_mapping_resolver;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS datasource_field_mappings");
    jdbcTemplate.execute("""
        CREATE TABLE datasource_field_mappings (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          source_table VARCHAR(100) NOT NULL,
          source_field VARCHAR(200) NOT NULL,
          target_field VARCHAR(100) NOT NULL,
          is_enabled TINYINT NOT NULL DEFAULT 1
        )
        """);
  }

  @Test
  void mapsRowsOnlyFromDatabaseMappings() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('私域客资管理表', '联系方式', 'phone', 1),
               ('私域客资管理表', '备注称呼', 'nickname', 1)
        """);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);

    Customer customer = resolver.mapRow("私域客资管理表", new SheetRow("row-1", Map.of(
        "联系方式", "13800000000",
        "备注称呼", "Alice")));

    assertThat(customer.getPhone()).isEqualTo("13800000000");
    assertThat(customer.getNickname()).isEqualTo("Alice");
  }

  @Test
  void rejectsRowsWhenDatabaseMappingsAreMissing() {
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);

    assertThatThrownBy(() -> resolver.mapRow("未配置表", new SheetRow("row-1", Map.of("phone", "13800000000"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no enabled field mappings");
  }

  @Test
  void mapRowResultReturnsAcceptedTagsAndUnmatchedMetadata() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('table_a', 'concerns', 'bodyConcerns', 1),
               ('table_a', 'phone', 'phone', 1)
        """);
    TagExchangeService exchangeService = Mockito.mock(TagExchangeService.class);
    TagExchangeResult exchange = new TagExchangeResult(
        Map.of("bodyConcerns", "URINE_LEAKAGE", "phone", "13800000000"),
        List.of(),
        List.of());
    Mockito.when(exchangeService.prepareInbound(
        Mockito.eq(com.privateflow.modules.tags.TagExchangeSourceType.EXTERNAL_SYNC),
        Mockito.eq("row-1"),
        Mockito.any(Map.class))).thenReturn(exchange);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate, exchangeService);

    FieldMappingResult result = resolver.mapRowResult("table_a", new SheetRow("row-1", Map.of(
        "concerns", "漏尿",
        "phone", "13800000000")));

    assertThat(result.customer().getPhone()).isEqualTo("13800000000");
    assertThat(result.customer().getBodyConcerns()).isEqualTo("URINE_LEAKAGE");
    assertThat(result.tagExchange()).isEqualTo(exchange);
    assertThat(result.mappedFields()).containsExactlyInAnyOrder("bodyConcerns", "phone");
  }

  @Test
  void mapsPlatformLeadTimeFromSmartSheetDateTimeFormat() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('assignment_table', '联系方式', 'phone', 1),
               ('assignment_table', '留资时间', 'platformLeadAt', 1)
        """);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);

    Customer customer = resolver.mapRow("assignment_table", new SheetRow("row-1", Map.of(
        "联系方式", "13800000000",
        "留资时间", "2026-08-14 10:30")));

    assertThat(customer.getPlatformLeadAt()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 14, 10, 30));
  }

  @Test
  void derivesExperienceCardTypeAndAssignmentMonthFromAssignmentRows() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('ASSIGNMENT:sheet', '手机号', 'phone', 1),
               ('ASSIGNMENT:sheet', '购买项目', 'purchasedProject', 1),
               ('ASSIGNMENT:sheet', '分配日期', 'assignedAt', 1)
        """);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);

    Customer customer = resolver.mapRow("ASSIGNMENT:sheet", new SheetRow("row-1", Map.of(
        "手机号", "13800000000",
        "购买项目", "孕妇按摩体验卡",
        "分配日期", "2025-01-08")));

    assertThat(customer.getPurchasedProject()).isEqualTo("孕妇按摩体验卡");
    assertThat(customer.getExperienceCardType()).isEqualTo("孕按");
    assertThat(customer.getAssignmentMonth()).isEqualTo("25年1月");
  }

  @Test
  void callbackValuesDoNotTreatAnOmittedFieldAsAnInstructionToClearIt() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('table_a', '联系方式', 'phone', 1),
               ('table_a', '备注称呼', 'nickname', 1)
        """);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);

    Map<String, String> values = resolver.mappedRawValues("table_a", new SheetRow("row-1", Map.of(
        "联系方式", "13800000000")));

    assertThat(values).containsExactly(Map.entry("phone", "13800000000"));
  }

  @Test
  void callbackApplyChangesOnlyReturnedMappedFields() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('table_a', '联系方式', 'phone', 1),
               ('table_a', '备注称呼', 'nickname', 1)
        """);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    customer.setNickname("before");

    assertThat(resolver.applyMappedValues(customer, Map.of("phone", "13800000001"), Set.of()))
        .containsExactly("phone");
    assertThat(customer.getPhone()).isEqualTo("13800000001");
    assertThat(customer.getNickname()).isEqualTo("before");
  }

  @Test
  void clearsInboundMappingsWhenReloadFailsInsteadOfUsingStaleSnapshot() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('table_a', 'phone', 'phone', 1)
        """);
    FieldMappingResolver resolver = new FieldMappingResolver(jdbcTemplate);
    jdbcTemplate.execute("DROP TABLE datasource_field_mappings");

    resolver.reload();

    assertThatThrownBy(() -> resolver.mapRow("table_a", new SheetRow("row-1", Map.of("phone", "13800000000"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no enabled field mappings");
  }

  @Test
  void configMappingReloadRunsAfterCommitAndNotAfterRollback() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('table_a', 'old_phone', 'phone', 1)
        """);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(TransactionTestConfiguration.class);
    context.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
    context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
    context.registerBean(FieldMappingResolver.class, () -> new FieldMappingResolver(jdbcTemplate));
    context.refresh();
    try {
      FieldMappingResolver resolver = context.getBean(FieldMappingResolver.class);
      org.springframework.context.ApplicationEventPublisher publisher = context;
      TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

      transaction.execute(status -> {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        jdbcTemplate.update("UPDATE datasource_field_mappings SET source_field = 'new_phone'");
        publisher.publishEvent(new com.privateflow.common.events.ConfigChangedEvent("datasource.field_mappings"));
        assertThat(resolver.mapRow("table_a", new SheetRow("row-1", Map.of("new_phone", "13800000000"))).getPhone())
            .isNull();
        return null;
      });
      assertThat(resolver.mapRow("table_a", new SheetRow("row-1", Map.of("new_phone", "13800000000"))).getPhone())
          .isEqualTo("13800000000");

      jdbcTemplate.update("UPDATE datasource_field_mappings SET source_field = 'old_phone'");
      resolver.reload();
      transaction.execute(status -> {
        jdbcTemplate.update("UPDATE datasource_field_mappings SET source_field = 'rolled_back_phone'");
        publisher.publishEvent(new com.privateflow.common.events.ConfigChangedEvent("datasource.field_mappings"));
        status.setRollbackOnly();
        return null;
      });
      assertThat(resolver.mapRow("table_a", new SheetRow("row-1", Map.of("old_phone", "13800000001"))).getPhone())
          .isEqualTo("13800000001");
      assertThat(resolver.mapRow("table_a", new SheetRow("row-1", Map.of("rolled_back_phone", "13800000001"))).getPhone())
          .isNull();
    } finally {
      context.close();
    }
  }

  @Configuration
  @EnableTransactionManagement
  static class TransactionTestConfiguration {
  }
}
