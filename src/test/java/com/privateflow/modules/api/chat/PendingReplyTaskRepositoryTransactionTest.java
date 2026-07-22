package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.annotation.AnnotationUtils;

class PendingReplyTaskRepositoryTransactionTest {

  @Test
  void createRollsBackTheTaskWhenCandidateInsertFails() throws NoSuchMethodException {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
        TransactionConfig.class)) {
      JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
      TransactionTemplate transactionTemplate = new TransactionTemplate(
          context.getBean(PlatformTransactionManager.class));
      transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      transactionTemplate.executeWithoutResult(status -> createSchema(jdbcTemplate));
      PendingReplyTaskRepository repository = context.getBean(PendingReplyTaskRepository.class);
      Method createMethod = PendingReplyTaskRepository.class.getMethod(
          "create", PendingReplyTaskDraft.class, int.class);

      assertThat(AopUtils.isAopProxy(repository)).isTrue();
      assertThat(AnnotationUtils.findAnnotation(createMethod, Transactional.class)).isNotNull();
      assertThatThrownBy(() -> repository.create(new PendingReplyTaskDraft(
          "reply-100-1",
          "keeper-1",
          "same-name customer",
          null,
          null,
          "TUAN_GOU",
          "customer_sheet",
          "I want to know more",
          List.of(Map.of("role", "client", "text", "I want to know more")),
          List.of(
              candidate("18800001111"),
              candidate("18800001111"))), 24))
          .isInstanceOf(RuntimeException.class);

      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_reply_tasks", Integer.class))
          .isZero();
    }
  }

  @Test
  void createWithDefaultTtlRollsBackTheTaskWhenCandidateInsertFails() throws NoSuchMethodException {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
        TransactionConfig.class)) {
      JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
      TransactionTemplate transactionTemplate = new TransactionTemplate(
          context.getBean(PlatformTransactionManager.class));
      transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      transactionTemplate.executeWithoutResult(status -> createSchema(jdbcTemplate));
      PendingReplyTaskRepository repository = context.getBean(PendingReplyTaskRepository.class);
      Method createMethod = PendingReplyTaskRepository.class.getMethod(
          "create", PendingReplyTaskDraft.class);

      assertThat(AopUtils.isAopProxy(repository)).isTrue();
      assertThat(AnnotationUtils.findAnnotation(createMethod, Transactional.class)).isNotNull();
      assertThatThrownBy(() -> repository.create(new PendingReplyTaskDraft(
          "reply-100-1",
          "keeper-1",
          "same-name customer",
          null,
          null,
          "TUAN_GOU",
          "customer_sheet",
          "I want to know more",
          List.of(Map.of("role", "client", "text", "I want to know more")),
          List.of(
              candidate("18800001111"),
              candidate("18800001111")))))
          .isInstanceOf(RuntimeException.class);

      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_reply_tasks", Integer.class))
          .isZero();
    }
  }

  private void createSchema(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute("DROP TABLE IF EXISTS pending_reply_task_candidates");
    jdbcTemplate.execute("DROP TABLE IF EXISTS pending_reply_tasks");
    jdbcTemplate.execute("""
        CREATE TABLE pending_reply_tasks (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          task_id CHAR(36) NOT NULL UNIQUE,
          reply_session_id VARCHAR(80) NOT NULL,
          username VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          recognized_nickname VARCHAR(255),
          recognized_phone VARCHAR(32),
          platform_identifier VARCHAR(255),
          lead_type VARCHAR(32),
          source_table VARCHAR(255),
          client_message TEXT NOT NULL,
          chat_context_json CLOB NOT NULL,
          selected_phone VARCHAR(32),
          result_json CLOB,
          error_code VARCHAR(32),
          generation_started_at TIMESTAMP,
          finished_at TIMESTAMP,
          expires_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          version INT NOT NULL DEFAULT 0
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE pending_reply_task_candidates (
          task_id BIGINT NOT NULL,
          phone VARCHAR(32) NOT NULL,
          rank_no SMALLINT NOT NULL,
          PRIMARY KEY (task_id, phone)
        )
        """);
  }

  private CustomerSummary candidate(String phone) {
    return new CustomerSummary(
        phone,
        phone,
        "same-name customer",
        "WECHAT",
        "TUAN_GOU",
        "keeper-1",
        LocalDateTime.of(2026, 7, 22, 10, 0),
        "store A",
        Confidence.HIGH);
  }

  @Configuration
  @EnableTransactionManagement
  static class TransactionConfig {

    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          "jdbc:h2:mem:pending_reply_tasks_tx;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
          "sa",
          "");
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    PendingReplyTaskRepository pendingReplyTaskRepository(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper) {
      return new PendingReplyTaskRepository(jdbcTemplate, objectMapper);
    }
  }
}
