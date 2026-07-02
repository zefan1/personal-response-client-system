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
        SELECT tag_value, tag_label, tag_description
        FROM personality_tags
        WHERE enabled = 1
        ORDER BY sort_order
        """,
        (rs, rowNum) -> new PersonalityTag(
            rs.getString("tag_value"),
            rs.getString("tag_label"),
            rs.getString("tag_description")));
  }

  public record PersonalityTag(String value, String label, String description) {
  }
}
