package com.privateflow.modules.skill.infra;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonalityTagRepository {

  private final JdbcTemplate jdbcTemplate;

  public PersonalityTagRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<PersonalityTag> findEnabled() {
    return jdbcTemplate.query("""
        SELECT v.tag_value, v.display_name AS tag_label, v.meaning AS tag_description
        FROM tag_values v
        JOIN tag_categories c ON c.id = v.category_id
        WHERE c.category_key = 'personality_type'
          AND c.is_enabled = 1 AND c.merged_into_id IS NULL
          AND v.is_enabled = 1 AND v.merged_into_id IS NULL
        ORDER BY v.sort_order, v.id
        """,
        (rs, rowNum) -> new PersonalityTag(
            rs.getString("tag_value"),
            rs.getString("tag_label"),
            rs.getString("tag_description")));
  }

  public record PersonalityTag(String value, String label, String description) {
  }
}
