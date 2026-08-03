package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WecomSmartSheetLiveAcceptanceServiceTest {

  @Test
  void executesTheControlledLiveAcceptanceSequence() {
    WecomSmartSheetConfig config = config();
    InMemoryApi api = new InMemoryApi(config);
    WecomSmartSheetFieldCatalog fields = new WecomSmartSheetFieldCatalog(api, config);
    WecomSmartSheetRecordClient records = new WecomSmartSheetRecordClient(
        config, api, fields, new WecomSmartSheetValueCodec(config));
    WecomSmartSheetLiveAcceptanceService service = new WecomSmartSheetLiveAcceptanceService(
        config, api, fields, records, Duration.ofSeconds(5), () -> "test-run");

    WecomSmartSheetLiveAcceptanceService.Report report = service.run();

    assertThat(report.createdRecordId()).isEqualTo("rec-acceptance");
    assertThat(report.querySucceeded()).isTrue();
    assertThat(report.updateSucceeded()).isTrue();
    assertThat(report.duplicatePrevented()).isTrue();
    assertThat(report.rereadSucceeded()).isTrue();
    assertThat(report.formulaProtectionConfirmed()).isTrue();
    assertThat(api.operations).containsExactly(
        "get_fields", "get_records", "get_records", "add_records", "update_records", "get_records", "get_records");
    assertThat(api.addCalls).isEqualTo(1);
    assertThat(api.updateCalls).isEqualTo(1);
    assertThat(api.addedUniqueValue).matches("198\\d{8}");
    assertThat(api.uniqueValue).matches("199\\d{8}");
  }

  private static WecomSmartSheetConfig config() {
    return new WecomSmartSheetConfig(
        "http://127.0.0.1", "corp", "secret", "doc", "sheet", "view", "Customers", "Unique", ZoneId.of("Asia/Shanghai"));
  }

  private static final class InMemoryApi extends WecomSmartSheetApiClient {

    private final List<String> operations = new ArrayList<>();
    private boolean created;
    private String addedUniqueValue;
    private String uniqueValue;
    private int addCalls;
    private int updateCalls;

    private InMemoryApi(WecomSmartSheetConfig config) {
      super(new ObjectMapper(), config, new WecomAccessTokenProvider(new ObjectMapper(), config));
    }

    @Override
    @SuppressWarnings("unchecked")
    public JsonNode post(String operation, Object body, Duration timeout) {
      operations.add(operation);
      return switch (operation) {
        case "get_fields" -> fields();
        case "get_records" -> records((Map<String, Object>) body);
        case "add_records" -> add((Map<String, Object>) body);
        case "update_records" -> update((Map<String, Object>) body);
        default -> throw new AssertionError("Unexpected operation: " + operation);
      };
    }

    private JsonNode fields() {
      ObjectNode root = JsonNodeFactory.instance.objectNode().put("errcode", 0).put("total", 2);
      ArrayNode items = root.putArray("fields");
      items.addObject().put("field_id", "f-unique").put("field_title", "Unique").put("field_type", "FIELD_TYPE_TEXT");
      items.addObject().put("field_id", "f-formula").put("field_title", "Formula").put("field_type", "FIELD_TYPE_FORMULA");
      return root;
    }

    private JsonNode records(Map<String, Object> request) {
      ObjectNode root = JsonNodeFactory.instance.objectNode()
          .put("errcode", 0)
          .put("has_more", false)
          .put("total", created ? 1 : 0);
      ArrayNode items = root.putArray("records");
      if (created && request.containsKey("field_ids")) {
        ObjectNode record = items.addObject().put("record_id", "rec-acceptance");
        record.putObject("values").putArray("f-unique")
            .addObject().put("type", "text").put("text", uniqueValue);
      }
      return root;
    }

    private JsonNode add(Map<String, Object> request) {
      addCalls++;
      List<Map<String, Object>> requestRecords = (List<Map<String, Object>>) request.get("records");
      Map<String, JsonNode> values = (Map<String, JsonNode>) requestRecords.get(0).get("values");
      uniqueValue = values.get("f-unique").path(0).path("text").asText();
      addedUniqueValue = uniqueValue;
      created = true;
      ObjectNode root = JsonNodeFactory.instance.objectNode().put("errcode", 0);
      root.putArray("records").addObject().put("record_id", "rec-acceptance");
      return root;
    }

    @SuppressWarnings("unchecked")
    private JsonNode update(Map<String, Object> request) {
      updateCalls++;
      List<Map<String, Object>> requestRecords = (List<Map<String, Object>>) request.get("records");
      Map<String, JsonNode> values = (Map<String, JsonNode>) requestRecords.get(0).get("values");
      uniqueValue = values.get("f-unique").path(0).path("text").asText();
      ObjectNode root = JsonNodeFactory.instance.objectNode().put("errcode", 0);
      root.putArray("records").addObject().put("record_id", "rec-acceptance");
      return root;
    }
  }
}
