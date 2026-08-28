package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MonthlyAssignmentTableRepositoryTest {

  private JdbcTemplate jdbc;
  private MonthlyAssignmentTableRepository repository;

  @BeforeEach
  void setUp() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:monthly_assignment;MODE=MySQL;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP TABLE IF EXISTS monthly_assignment_tables");
    jdbc.execute("""
        CREATE TABLE monthly_assignment_tables (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          table_name VARCHAR(200) NOT NULL UNIQUE,
          month_key VARCHAR(7) NOT NULL,
          document_id VARCHAR(200) NOT NULL,
          sheet_id VARCHAR(200) NOT NULL,
          view_id VARCHAR(200) NOT NULL,
          unique_field_title VARCHAR(200) NOT NULL DEFAULT '',
          document_url VARCHAR(1000) NOT NULL,
          status VARCHAR(20) NOT NULL,
          error_message VARCHAR(500),
          created_by VARCHAR(100),
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
          activated_at DATETIME,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL
        )
        """);
    repository = new MonthlyAssignmentTableRepository(jdbc);
  }

  @Test
  void createPendingReturnsItsOwnGeneratedIdAndActivateArchivesOnlyPreviousActiveRow() {
    long oldId = repository.createPending("8月分配", "2026-08", "admin");
    repository.markReady(oldId, "doc-old", "sheet-old", "view-old", "手机号码", "https://doc.weixin.qq.com/old");
    repository.activate(oldId);
    long newId = repository.createPending("9月新客分配", "2026-09", "keeper");
    repository.markReady(newId, "doc-new", "sheet-new", "view-new", "手机号码", "https://doc.weixin.qq.com/new");

    repository.activate(newId);

    assertThat(newId).isGreaterThan(oldId);
    assertThat(repository.findById(oldId)).get().extracting(MonthlyAssignmentTable::status).isEqualTo("ARCHIVED");
    assertThat(repository.findById(newId)).get().extracting(MonthlyAssignmentTable::status).isEqualTo("ACTIVE");
    assertThat(repository.findById(newId).orElseThrow().activatedAt()).isNotNull();
  }

  @Test
  void deletesOnlyTheRequestedHistoryRow() {
    long oldId = repository.createPending("8月分配", "2026-08", "admin");
    long otherId = repository.createPending("9月分配", "2026-09", "admin");

    assertThat(repository.delete(oldId)).isEqualTo(1);
    assertThat(repository.findById(oldId)).isEmpty();
    assertThat(repository.findById(otherId)).isPresent();
  }
}
