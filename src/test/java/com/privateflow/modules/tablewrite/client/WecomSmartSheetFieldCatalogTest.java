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
          {"field_id":"fName","field_title":"\u5907\u6ce8\u79f0\u547c","field_type":"FIELD_TYPE_TEXT"},
          {"field_id":"fPhone","field_title":"\u8054\u7cfb\u65b9\u5f0f","field_type":"FIELD_TYPE_PHONE_NUMBER"},
          {"field_id":"fNext","field_title":"\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4","field_type":"FIELD_TYPE_DATE_TIME","property_date_time":{"format":"yyyy-mm-dd hh:mm"}},
          {"field_id":"fStage","field_title":"\u5ba2\u6237\u9636\u6bb5","field_type":"FIELD_TYPE_SINGLE_SELECT","property_single_select":{"options":[{"id":"opt1","text":"\u8ddf\u8fdb\u4e2d","style":1}]}},
          {"field_id":"fFormula","field_title":"\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09","field_type":"FIELD_TYPE_FORMULA"}
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
    assertThat(fields.get("\u5907\u6ce8\u79f0\u547c").fieldId()).isEqualTo("fName");
    assertThat(fields.get("\u5907\u6ce8\u79f0\u547c").type()).isEqualTo("FIELD_TYPE_TEXT");
    assertThat(fields.get("\u8054\u7cfb\u65b9\u5f0f").fieldId()).isEqualTo("fPhone");
    assertThat(fields.get("\u8054\u7cfb\u65b9\u5f0f").type()).isEqualTo("FIELD_TYPE_PHONE_NUMBER");
    assertThat(fields.get("\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4").fieldId()).isEqualTo("fNext");
    assertThat(fields.get("\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4").type()).isEqualTo("FIELD_TYPE_DATE_TIME");
    assertThat(fields.get("\u4e0b\u6b21\u8ddf\u8fdb\u65f6\u95f4").dateTimeIncludesTime()).isTrue();
    assertThat(api.lastResponse.at("/fields/3/property_single_select/options/0/style").isIntegralNumber()).isTrue();
    assertThat(api.lastResponse.at("/fields/3/property_single_select/options/0/style").intValue()).isEqualTo(1);
    assertThat(fields.get("\u5ba2\u6237\u9636\u6bb5").fieldId()).isEqualTo("fStage");
    assertThat(fields.get("\u5ba2\u6237\u9636\u6bb5").type()).isEqualTo("FIELD_TYPE_SINGLE_SELECT");
    assertThat(fields.get("\u5ba2\u6237\u9636\u6bb5").optionId(" \u8ddf\u8fdb\u4e2d ")).contains("opt1");
    assertThat(fields.get("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09").fieldId()).isEqualTo("fFormula");
    assertThat(fields.get("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09").type()).isEqualTo("FIELD_TYPE_FORMULA");
    assertThat(fields.get("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09").writable()).isFalse();
    assertThatThrownBy(() -> catalog.requireWritable("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09", Duration.ofSeconds(1)))
        .hasMessageContaining("\u662f\u5426\u52a0\u5fae\uff08\u516c\u5f0f\uff09");
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
  void startsTheCacheLifetimeAfterTheSuccessfulLoadCompletes() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    ScriptedClient api = client(
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"First\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}",
        "{\"errcode\":0,\"total\":1,\"fields\":[{\"field_id\":\"f2\",\"field_title\":\"Second\",\"field_type\":\"FIELD_TYPE_TEXT\"}]}");
    api.advanceClockBeforeFirstResponse(clock, Duration.ofMinutes(4));
    WecomSmartSheetFieldCatalog catalog = catalog(api, clock);

    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(api.bodies).hasSize(1);
    clock.advance(Duration.ofMinutes(4).plusSeconds(59));
    assertThat(catalog.visibleFields(Duration.ofSeconds(1))).containsKey("First");
    assertThat(api.bodies).hasSize(1);
    clock.advance(Duration.ofSeconds(1));
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
    private JsonNode lastResponse;
    private Runnable beforeFirstResponse = () -> {};

    private ScriptedClient(String... source) throws Exception {
      super(JSON, config(), null);
      for (String value : source) {
        responses.add(JSON.readTree(value));
      }
    }

    @Override public JsonNode post(String operation, Object body, Duration timeout) {
      bodies.add(JSON.valueToTree(body));
      if (bodies.size() == 1) {
        beforeFirstResponse.run();
      }
      lastResponse = responses.removeFirst();
      return lastResponse;
    }

    void advanceClockBeforeFirstResponse(MutableClock clock, Duration duration) {
      beforeFirstResponse = () -> clock.advance(duration);
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
