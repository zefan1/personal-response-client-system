package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WecomSmartSheetFieldCatalogTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void loadsOfficialVisibleFieldShapeAndRejectsFormulaWrites() throws Exception {
    ScriptedClient api = client("""
        {"errcode":0,"total":5,"fields":[
          {"field_id":"f-text","field_title":"Name","field_type":"FIELD_TYPE_TEXT"},
          {"field_id":"f-phone","field_title":"Phone","field_type":"FIELD_TYPE_PHONE_NUMBER"},
          {"field_id":"f-date","field_title":"Follow up","field_type":"FIELD_TYPE_DATE_TIME","property_date_time":{"format":"yyyy-mm-dd hh:mm"}},
          {"field_id":"f-tier","field_title":"Tier","field_type":"FIELD_TYPE_SINGLE_SELECT","property_single_select":{"options":[{"id":"o-vip","text":"VIP","style":"blue"}]}},
          {"field_id":"f-formula","field_title":"Score","field_type":"FIELD_TYPE_FORMULA"}
        ]}""");
    WecomSmartSheetFieldCatalog catalog = catalog(api, Clock.systemUTC());

    Map<String, WecomSmartSheetField> fields = catalog.visibleFields(Duration.ofSeconds(1));

    assertThat(api.bodies).singleElement().satisfies(body -> {
      assertThat(body.path("docid").asText()).isEqualTo("doc-1");
      assertThat(body.path("sheet_id").asText()).isEqualTo("sheet-1");
      assertThat(body.path("view_id").asText()).isEqualTo("vView");
      assertThat(body.path("offset").asInt()).isZero();
      assertThat(body.path("limit").asInt()).isEqualTo(1000);
    });
    assertThat(fields.get("Name").fieldId()).isEqualTo("f-text");
    assertThat(fields.get("Phone").writable()).isTrue();
    assertThat(fields.get("Follow up").dateTimeIncludesTime()).isTrue();
    assertThat(fields.get("Tier").optionId(" VIP ")).contains("o-vip");
    assertThat(fields.get("Score").writable()).isFalse();
    assertThatThrownBy(() -> catalog.requireWritable("Score", Duration.ofSeconds(1)))
        .hasMessageContaining("Score");
  }

  @Test
  void paginatesWithLoadedCountAndReturnsImmutableMergedFields() throws Exception {
    ScriptedClient api = client(
        "{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"One\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":2,\"fields\":[{\"field_id\":\"f2\",\"field_title\":\"Two\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");

    Map<String, WecomSmartSheetField> fields = catalog(api, Clock.systemUTC()).visibleFields(Duration.ofSeconds(1));

    assertThat(fields).containsKeys("One", "Two");
    assertThat(api.bodies).extracting(body -> body.path("offset").asInt()).containsExactly(0, 1);
    assertThatThrownBy(() -> fields.put("Three", fields.get("One"))).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void cachesForFiveMinutesThenRefreshes() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    ScriptedClient api = client(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"First\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f2\",\"field_title\":\"Second\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");
    WecomSmartSheetFieldCatalog catalog = catalog(api, clock);

    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    clock.advance(Duration.ofMinutes(5));
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("Second");
    assertThat(api.bodies).hasSize(2);
  }

  @Test
  void rejectsInvalidAndStalledCatalogPagesWithoutResponseContents() throws Exception {
    List<String> invalidResponses = List.of(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"Same\",\"field_type\":\"FIELD_TYPE_TEXT\"},{\"field_id\":\"f2\",\"field_title\":\"Same\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"Pick\",\"field_type\":\"FIELD_TYPE_SELECT\",\"property_select\":{\"options\":[{\"id\":\"o1\",\"text\":\"A\",\"style\":\"x\"},{\"id\":\"o2\",\"text\":\"A\",\"style\":\"y\"}]}}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\" \",\"field_title\":\"Bad\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":\"one\",\"fields\":[]}",
        "{\"errcode\":0,\"total\":1,\"fields\":{}}");
    for (String response : invalidResponses) {
      assertThatThrownBy(() -> catalog(client(response), Clock.systemUTC()).visibleFields(Duration.ofSeconds(1)))
          .hasMessageNotContaining(response).hasMessageNotContaining("errcode");
    }
    assertThatThrownBy(() -> catalog(client("{\"errcode\":0,\"total\":1,\"fields\":[]}"), Clock.systemUTC())
        .visibleFields(Duration.ofSeconds(1))).hasMessageNotContaining("fields");
  }

  @Test
  void distinguishesUnknownAndReadOnlyTitlesWithoutExposingJson() throws Exception {
    WecomSmartSheetFieldCatalog catalog = catalog(client("""
        {"errcode":0,"total":1,"fields":[{"field_id":"f1","field_title":"Formula title","field_type":"FIELD_TYPE_FORMULA"}]}"""), Clock.systemUTC());

    assertThatThrownBy(() -> catalog.requireWritable("Not present", Duration.ofSeconds(1)))
        .hasMessageContaining("Not present").hasMessageNotContaining("errcode");
    assertThatThrownBy(() -> catalog.requireWritable("Formula title", Duration.ofSeconds(1)))
        .hasMessageContaining("Formula title").hasMessageNotContaining("errcode");
  }

  private static WecomSmartSheetFieldCatalog catalog(ScriptedClient api, Clock clock) {
    return new WecomSmartSheetFieldCatalog(api, config(), clock);
  }

  private static WecomSmartSheetConfig config() {
    return new WecomSmartSheetConfig("http://127.0.0.1", "corp", "secret", "doc-1", "sheet-1", "vView",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }

  private static ScriptedClient client(String... responses) throws Exception {
    return new ScriptedClient(responses);
  }

  private static final class ScriptedClient extends WecomSmartSheetApiClient {
    private final ArrayDeque<JsonNode> responses = new ArrayDeque<>();
    private final List<JsonNode> bodies = new ArrayList<>();

    private ScriptedClient(String... source) throws Exception {
      super(JSON, config(), null);
      for (String value : source) {
        responses.add(JSON.readTree(value));
      }
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      bodies.add(JSON.valueToTree(body));
      return responses.removeFirst();
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private MutableClock(Instant instant) { this.instant = instant; }
    void advance(Duration duration) { instant = instant.plus(duration); }
    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
  }
}
