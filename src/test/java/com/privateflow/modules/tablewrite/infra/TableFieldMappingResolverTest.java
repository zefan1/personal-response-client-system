package com.privateflow.modules.tablewrite.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.tablewrite.TableWriteException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TableFieldMappingResolverTest {

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:table_field_mapping_resolver;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
  void mapsOnlyConfiguredFieldsToSourceColumns() {
    jdbcTemplate.update("""
        INSERT INTO datasource_field_mappings (source_table, source_field, target_field, is_enabled)
        VALUES ('私域客资管理表', '联系方式', 'phone', 1)
        """);
    TableFieldMappingResolver resolver = new TableFieldMappingResolver(jdbcTemplate);

    Map<String, Object> sourceFields = resolver.toSourceFields("私域客资管理表", Map.of(
        "phone", "13800000000",
        "customerStage", "待联系"));

    assertThat(sourceFields).containsEntry("联系方式", "13800000000");
    assertThat(sourceFields).doesNotContainKeys("phone", "customerStage");
  }

  @Test
  void rejectsTableWritesWhenMappingsAreMissing() {
    TableFieldMappingResolver resolver = new TableFieldMappingResolver(jdbcTemplate);

    assertThatThrownBy(() -> resolver.toSourceFields("未配置表", Map.of("phone", "13800000000")))
        .isInstanceOf(TableWriteException.class)
        .hasMessageContaining("no enabled field mappings");
  }
}
