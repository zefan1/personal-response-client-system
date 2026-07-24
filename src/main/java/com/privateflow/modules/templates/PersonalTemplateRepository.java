package com.privateflow.modules.templates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PersonalTemplateRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public PersonalTemplateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long insertPersonal(
      String ownerUsername,
      String title,
      String body,
      TemplateMetadata metadata,
      String sourceReplySessionId) {
    LocalDateTime now = LocalDateTime.now();
    return insertAndReturnId("""
        INSERT INTO personal_templates (
          owner_username, title, body, channel_code, scene, lead_type, labels_json,
          source_reply_session_id, usage_count, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
        """, statement -> {
      statement.setString(1, ownerUsername);
      statement.setString(2, title);
      statement.setString(3, body);
      statement.setString(4, metadata.channelCode());
      statement.setString(5, metadata.scene());
      statement.setString(6, metadata.leadType());
      statement.setString(7, labelsJson(metadata));
      statement.setString(8, sourceReplySessionId);
      statement.setTimestamp(9, Timestamp.valueOf(now));
      statement.setTimestamp(10, Timestamp.valueOf(now));
    });
  }

  public void updatePersonal(
      long id,
      String ownerUsername,
      String title,
      String body,
      TemplateMetadata metadata,
      String sourceReplySessionId) {
    jdbcTemplate.update("""
        UPDATE personal_templates
        SET title = ?, body = ?, channel_code = ?, scene = ?, lead_type = ?, labels_json = ?,
            source_reply_session_id = ?, updated_at = ?
        WHERE id = ? AND owner_username = ?
        """,
        title,
        body,
        metadata.channelCode(),
        metadata.scene(),
        metadata.leadType(),
        labelsJson(metadata),
        sourceReplySessionId,
        Timestamp.valueOf(LocalDateTime.now()),
        id,
        ownerUsername);
  }

  public long insertCandidate(
      long personalTemplateId,
      String ownerUsername,
      String originalAiReply,
      String editedTitle,
      String editedBody,
      TemplateMetadata metadata) {
    return insertAndReturnId("""
        INSERT INTO template_promotion_candidates (
          personal_template_id, owner_username, original_ai_reply, edited_title, edited_body,
          metadata_json, status, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, 'CANDIDATE', ?)
        """, statement -> {
      statement.setLong(1, personalTemplateId);
      statement.setString(2, ownerUsername);
      statement.setString(3, originalAiReply);
      statement.setString(4, editedTitle);
      statement.setString(5, editedBody);
      statement.setString(6, metadataJson(metadata));
      statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
    });
  }

  public Optional<PersonalTemplate> findPersonal(long id, String ownerUsername) {
    return jdbcTemplate.query("""
        SELECT id, title, body, channel_code, scene, lead_type, labels_json,
               source_reply_session_id, usage_count, created_at, updated_at
        FROM personal_templates
        WHERE id = ? AND owner_username = ?
        """, (rs, rowNum) -> new PersonalTemplate(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("body"),
        metadata(rs.getString("channel_code"), rs.getString("scene"), rs.getString("lead_type"), rs.getString("labels_json")),
        rs.getString("source_reply_session_id"),
        rs.getLong("usage_count"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()), id, ownerUsername).stream().findFirst();
  }

  public List<PersonalTemplate> findMine(String ownerUsername) {
    return jdbcTemplate.query("""
        SELECT id, title, body, channel_code, scene, lead_type, labels_json,
               source_reply_session_id, usage_count, created_at, updated_at
        FROM personal_templates
        WHERE owner_username = ?
        ORDER BY updated_at DESC, id DESC
        """, (rs, rowNum) -> new PersonalTemplate(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("body"),
        metadata(rs.getString("channel_code"), rs.getString("scene"), rs.getString("lead_type"), rs.getString("labels_json")),
        rs.getString("source_reply_session_id"),
        rs.getLong("usage_count"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()), ownerUsername);
  }

  public Optional<TemplatePromotionCandidate> findCandidate(long candidateId) {
    return jdbcTemplate.query(candidateSelect() + " WHERE candidate.id = ?", this::candidate, candidateId)
        .stream().findFirst();
  }

  private String candidateSelect() {
    return """
        SELECT candidate.id, candidate.personal_template_id, candidate.owner_username,
               candidate.original_ai_reply, candidate.edited_title, candidate.edited_body,
               candidate.metadata_json, candidate.status, candidate.decided_by,
               candidate.decided_at, candidate.created_at, personal.usage_count
        FROM template_promotion_candidates candidate
        JOIN personal_templates personal ON personal.id = candidate.personal_template_id
        """;
  }

  private TemplatePromotionCandidate candidate(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    Timestamp decidedAt = rs.getTimestamp("decided_at");
    return new TemplatePromotionCandidate(
        rs.getLong("id"),
        rs.getLong("personal_template_id"),
        rs.getString("owner_username"),
        rs.getString("original_ai_reply"),
        rs.getString("edited_title"),
        rs.getString("edited_body"),
        metadataFromJson(rs.getString("metadata_json")),
        TemplatePromotionCandidateStatus.valueOf(rs.getString("status")),
        rs.getString("decided_by"),
        decidedAt == null ? null : decidedAt.toLocalDateTime(),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getLong("usage_count"));
  }

  private long insertAndReturnId(String sql, StatementSetter setter) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      setter.set(statement);
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey(), "generated key is required").longValue();
  }

  private String labelsJson(TemplateMetadata metadata) {
    try {
      return objectMapper.writeValueAsString(metadata.labels());
    } catch (Exception exception) {
      throw new IllegalStateException("cannot serialize template labels", exception);
    }
  }

  private String metadataJson(TemplateMetadata metadata) {
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (Exception exception) {
      throw new IllegalStateException("cannot serialize template metadata", exception);
    }
  }

  private TemplateMetadata metadata(String channelCode, String scene, String leadType, String labelsJson) {
    return new TemplateMetadata(channelCode, scene, leadType, labelsFromJson(labelsJson));
  }

  private TemplateMetadata metadataFromJson(String metadataJson) {
    try {
      TemplateMetadata metadata = objectMapper.readValue(metadataJson, TemplateMetadata.class);
      return metadata == null ? new TemplateMetadata(null, null, null, List.of()) : metadata;
    } catch (Exception exception) {
      throw new IllegalStateException("cannot read template metadata", exception);
    }
  }

  private List<String> labelsFromJson(String labelsJson) {
    try {
      List<String> labels = objectMapper.readValue(labelsJson, new TypeReference<List<String>>() { });
      return labels == null ? List.of() : labels;
    } catch (Exception exception) {
      throw new IllegalStateException("cannot read template labels", exception);
    }
  }

  @FunctionalInterface
  private interface StatementSetter {
    void set(PreparedStatement statement) throws java.sql.SQLException;
  }
}
