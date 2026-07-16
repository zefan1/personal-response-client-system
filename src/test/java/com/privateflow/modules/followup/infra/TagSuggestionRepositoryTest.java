package com.privateflow.modules.followup.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TagSuggestionRepositoryTest {

  @Test
  void formalTargetStoresDirectoryValueAndValidatedStatus() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    TagSuggestionRepository repository = new TagSuggestionRepository(jdbc);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(99L);

    repository.upsertPending("13800000000", 50L, 51L, "高意向", 9L, 7);

    verify(jdbc).update(
        contains("tag_value_id"),
        eq("13800000000"), eq("13800000000"), eq("高意向"), eq(51L), eq(9L));
  }
}
