package com.privateflow.modules.profile.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.ProfileUpdatedEvent;
import com.privateflow.modules.tags.LegacyCustomerTagSynchronizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProfileWriterTest {

  @Test
  void writeByCustomerIdPublishesThePersistedPhoneForCacheRefresh() {
    JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    ProfileFieldRegistry fieldRegistry = org.mockito.Mockito.mock(ProfileFieldRegistry.class);
    ApplicationEventPublisher eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    LegacyCustomerTagSynchronizer tagSynchronizer = org.mockito.Mockito.mock(LegacyCustomerTagSynchronizer.class);
    ProfileFieldRegistry.FieldSpec spec = new ProfileFieldRegistry.FieldSpec("nickname", "nickname", String.class);
    when(fieldRegistry.supports("nickname")).thenReturn(true);
    when(fieldRegistry.spec("nickname")).thenReturn(spec);
    when(fieldRegistry.normalizeValue("nickname", "New name")).thenReturn("New name");
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("phone")
            ? List.of("13800000042")
            : List.of(8));

    ProfileWriter writer = new ProfileWriter(jdbcTemplate, fieldRegistry, eventPublisher, tagSynchronizer);
    writer.writeByCustomerId(42L, Map.of("nickname", "New name"), 7, true);

    verify(eventPublisher).publishEvent(new ProfileUpdatedEvent("13800000042", List.of("nickname")));
  }
}
