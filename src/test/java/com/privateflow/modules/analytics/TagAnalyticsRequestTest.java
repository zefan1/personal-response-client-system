package com.privateflow.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.admin.CustomerSearchRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagAnalyticsRequestTest {

  @Test
  void defensivelyCopiesTeamIdsAndCreatesZeroResponse() {
    ArrayList<Long> teamIds = new ArrayList<>(List.of(11L));
    TagAnalyticsRequest request = new TagAnalyticsRequest(
        new CustomerSearchRequest(null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null),
        teamIds,
        null,
        null,
        null);
    teamIds.add(12L);

    TagAnalyticsWindow window = new TagAnalyticsWindow(
        LocalDateTime.of(2026, 7, 10, 12, 0),
        LocalDateTime.of(2026, 7, 16, 12, 0),
        TagTrendGranularity.DAY);
    TagAnalyticsResponse response = TagAnalyticsResponse.empty(window);

    assertThat(request.teamLeaderIds()).containsExactly(11L);
    assertThat(response.summary().matchedCustomerCount()).isZero();
    assertThat(response.categories()).isEmpty();
    assertThat(response.appliedWindow().tagFrom()).isEqualTo(window.from());
  }
}
