package com.privateflow.modules.quicksearch.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.quicksearch.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class QuickSearchAdminRepositoryTest {

  @Test
  void createKeepsEntryLinksForLocationAndMiniProgram() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(21L);
    QuickSearchAdminRepository repository = new QuickSearchAdminRepository(jdbc);

    long locationId = repository.create(new QuickSearchItemRequest(
        ContentType.LOCATION,
        "GENERAL",
        "万江店",
        "shop01",
        "导航到门店",
        " https://map.example.com/shop01 ",
        3,
        true,
        null), "admin");
    long miniProgramId = repository.create(new QuickSearchItemRequest(
        ContentType.MINI_PROGRAM,
        "GENERAL",
        "预约小程序",
        "book01",
        "打开小程序预约",
        "pages/booking/index",
        4,
        true,
        null), "admin");

    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), values.capture());
    assertThat(locationId).isEqualTo(21L);
    assertThat(miniProgramId).isEqualTo(21L);
    assertThat(values.getAllValues().get(0)[5]).isEqualTo("https://map.example.com/shop01");
    assertThat(values.getAllValues().get(1)[5]).isEqualTo("pages/booking/index");
  }

  @Test
  void createDropsLinkForPlainContentTypes() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(22L);
    QuickSearchAdminRepository repository = new QuickSearchAdminRepository(jdbc);

    repository.create(new QuickSearchItemRequest(
        ContentType.TEMPLATE,
        "GENERAL",
        "开场",
        "hi01",
        "您好",
        "https://not-used.example.com",
        1,
        true,
        null), "admin");

    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), values.capture());
    assertThat(values.getValue()[5]).isNull();
  }

  @Test
  void updateChangesContentTypeWhenRequested() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    QuickSearchAdminRepository repository = new QuickSearchAdminRepository(jdbc);

    repository.update(23L, new QuickSearchItemRequest(
        ContentType.IMAGE,
        "GENERAL",
        "活动",
        "aaaaa",
        "这是我们最新的活动",
        "/uploads/quick-search/activity.jpg",
        99,
        true,
        null));

    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), values.capture());
    assertThat(values.getValue()[0]).isEqualTo("IMAGE");
  }
}
