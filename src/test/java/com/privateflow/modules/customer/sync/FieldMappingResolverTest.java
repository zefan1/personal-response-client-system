package com.privateflow.modules.customer.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.customer.Customer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FieldMappingResolverTest {

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
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
}
