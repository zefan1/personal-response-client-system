package com.privateflow.modules.templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PersonalTemplateRepositoryTest {

  private PersonalTemplateRepository repository;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:personal_templates;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS team_template_publications");
    jdbcTemplate.execute("DROP TABLE IF EXISTS quick_search_items");
    jdbcTemplate.execute("DROP TABLE IF EXISTS template_promotion_candidates");
    jdbcTemplate.execute("DROP TABLE IF EXISTS personal_templates");
    jdbcTemplate.execute("""
        CREATE TABLE personal_templates (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          owner_username VARCHAR(64) NOT NULL,
          title VARCHAR(120) NOT NULL,
          body CLOB NOT NULL,
          channel_code VARCHAR(100),
          scene VARCHAR(100),
          lead_type VARCHAR(100),
          labels_json CLOB NOT NULL,
          source_reply_session_id VARCHAR(80),
          usage_count BIGINT NOT NULL DEFAULT 0,
          created_at TIMESTAMP NOT NULL,
          updated_at TIMESTAMP NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE template_promotion_candidates (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          personal_template_id BIGINT NOT NULL,
          owner_username VARCHAR(64) NOT NULL,
          original_ai_reply CLOB NOT NULL,
          edited_title VARCHAR(120) NOT NULL,
          edited_body CLOB NOT NULL,
          metadata_json CLOB NOT NULL,
          status VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
          decided_by VARCHAR(64),
          decided_at TIMESTAMP,
          created_at TIMESTAMP NOT NULL,
          CONSTRAINT fk_candidate_personal_template FOREIGN KEY (personal_template_id)
            REFERENCES personal_templates(id)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE quick_search_items (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          content_type VARCHAR(32) NOT NULL,
          lead_type VARCHAR(32) NOT NULL,
          title VARCHAR(200) NOT NULL,
          shortcut_code VARCHAR(64) NOT NULL,
          content CLOB NOT NULL,
          sort_order INT NOT NULL DEFAULT 0,
          is_enabled TINYINT NOT NULL DEFAULT 1
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE team_template_publications (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          candidate_id BIGINT NOT NULL UNIQUE,
          quick_search_item_id BIGINT NOT NULL UNIQUE,
          published_by VARCHAR(64) NOT NULL,
          published_at TIMESTAMP NOT NULL
        )
        """);
    repository = new PersonalTemplateRepository(jdbcTemplate, new ObjectMapper());
  }

  @Test
  void keepsAnImmutableCandidateSnapshotWhenThePersonalTemplateChanges() {
    TemplateMetadata metadata = new TemplateMetadata(
        "wecom", "new-lead", "LEAD", List.of("budget-confirmed"));
    long templateId = repository.insertPersonal(
        "keeper-a", "Opening", "Edited first body", metadata, "reply-session-1");
    long candidateId = repository.insertCandidate(
        templateId, "keeper-a", "Original AI body", "Opening", "Edited first body", metadata);

    repository.updatePersonal(
        templateId, "keeper-a", "Updated title", "Updated body", metadata, "reply-session-2");

    TemplatePromotionCandidate snapshot = repository.findCandidate(candidateId).orElseThrow();
    assertThat(snapshot.editedTitle()).isEqualTo("Opening");
    assertThat(snapshot.editedBody()).isEqualTo("Edited first body");
    assertThat(snapshot.originalAiReply()).isEqualTo("Original AI body");
    assertThat(repository.findMine("keeper-a")).singleElement()
        .extracting(PersonalTemplate::title, PersonalTemplate::body)
        .containsExactly("Updated title", "Updated body");
  }

  @Test
  void returnsOnlyPublishedTeamTemplatesAndTracksTheirUseAgainstTheSourceTemplate() {
    TemplateMetadata metadata = new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm"));
    long templateId = repository.insertPersonal("keeper-a", "Opening", "Edited body", metadata, "reply-session-1");
    long candidateId = repository.insertCandidate(
        templateId, "keeper-a", "Original AI body", "Opening", "Edited body", metadata);
    jdbcTemplate.update("""
        INSERT INTO quick_search_items (
          content_type, lead_type, title, shortcut_code, content, sort_order, is_enabled
        ) VALUES ('TEMPLATE', 'LEAD', 'Team opening', 'TM42', 'Edited body', 0, 1)
        """);
    Long quickSearchItemId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM quick_search_items", Long.class);
    repository.insertPublication(candidateId, quickSearchItemId, "admin-a");

    assertThat(repository.findPublishedTeamTemplates()).singleElement()
        .extracting(TeamTemplate::quickSearchItemId, TeamTemplate::promotionCandidateId, TeamTemplate::title)
        .containsExactly(quickSearchItemId, candidateId, "Team opening");
    assertThat(repository.incrementTeamUsage(quickSearchItemId)).isTrue();
    assertThat(repository.findMine("keeper-a")).singleElement()
        .extracting(PersonalTemplate::usageCount)
        .isEqualTo(1L);
  }
}
