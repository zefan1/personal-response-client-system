package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.profile.config.ProfileConfig;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagDirectorySnapshot;
import com.privateflow.modules.tags.TagImpact;
import com.privateflow.modules.tags.TagSelectionMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfidenceRouterTest {

  @Test
  void blocksAllDatabaseBoundTagFieldsFromLegacyProfileUpdates() {
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    when(configProvider.get()).thenReturn(new ProfileConfig(
        List.of("nickname", "intentLevel"), 8000, 5, 7, "0 0 3 * * *", 20, 5, 500));
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    TagCategory boundCategory = new TagCategory(
        1L, "intent_level", "意向等级", "intentLevel", false, true, 1, List.of(), now, now);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(boundCategory.withImpact(TagImpact.empty())),
        Instant.parse("2026-07-15T05:00:00Z")));
    ConfidenceRouter router = new ConfidenceRouter(configProvider, new ProfileFieldRegistry(), directoryService);

    RoutedProfileUpdates routed = router.route(new ProfileUpdates(Map.of(
        "nickname", new FieldUpdate("Alice", "HIGH"),
        "intentLevel", new FieldUpdate("HIGH", "HIGH"))));

    assertThat(routed.high()).containsKey("nickname").doesNotContainKey("intentLevel");
  }
}
