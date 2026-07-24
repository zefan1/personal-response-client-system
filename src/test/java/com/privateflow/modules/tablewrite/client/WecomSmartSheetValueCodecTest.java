package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WecomSmartSheetValueCodecTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final WecomSmartSheetValueCodec codec = new WecomSmartSheetValueCodec(config());

  @Test
  void encodesAndDecodesTextIncludingExplicitEmptyClear() throws Exception {
    WecomSmartSheetField field = field("Name", "FIELD_TYPE_TEXT", Map.of(), false);
    assertThat(codec.decode(field, JSON.readTree("[{\"type\":\"text\",\"text\":\"A\"},{\"type\":\"link\",\"text\":\"B\"}]"))).isEqualTo("AB");
    assertThat(codec.encode(field, "").toString()).isEqualTo("[{\"type\":\"text\",\"text\":\"\"}]");
  }

  @Test
  void encodesPhoneAndEmailAsText() {
    assertThat(codec.encode(field("Phone", "FIELD_TYPE_PHONE_NUMBER", Map.of(), false), "13900000000").asText()).isEqualTo("13900000000");
    assertThat(codec.decode(field("Email", "FIELD_TYPE_EMAIL", Map.of(), false), JSON.getNodeFactory().textNode("a@b.test"))).isEqualTo("a@b.test");
  }

  @Test
  void canonicalizesNumbersAndRejectsInvalidWithoutInputLeak() throws Exception {
    WecomSmartSheetField field = field("Amount", "FIELD_TYPE_NUMBER", Map.of(), false);
    assertThat(codec.decode(field, JSON.readTree("1.2300"))).isEqualTo("1.23");
    assertThat(codec.encode(field, " 001.2300 ").toString()).isEqualTo("1.23");
    assertThat(codec.encode(field, "1E+3").toString()).isEqualTo("1000");
    assertThatThrownBy(() -> codec.encode(field, "PII-987654321"))
        .hasMessageContaining("Amount").hasMessageNotContaining("PII-987654321");
  }

  @Test
  void canonicalizesAllowedNumericWrappersAndRejectsUnsafeNumberSizes() {
    WecomSmartSheetField field = field("Amount", "FIELD_TYPE_NUMBER", Map.of(), false);
    assertThat(codec.encode(field, 0.1f).toString()).isEqualTo("0.1");
    assertThat(codec.encode(field, 0.1d).toString()).isEqualTo("0.1");
    assertThat(codec.encode(field, (byte) 7).toString()).isEqualTo("7");
    assertThat(codec.encode(field, (short) 8).toString()).isEqualTo("8");
    assertThat(codec.encode(field, 9).toString()).isEqualTo("9");
    assertThat(codec.encode(field, 10L).toString()).isEqualTo("10");
    assertThat(codec.encode(field, new BigInteger("11")).toString()).isEqualTo("11");
    assertThat(codec.encode(field, new BigDecimal("12.3400")).toString()).isEqualTo("12.34");
    assertSafeFailure(() -> codec.encode(field, "1E+100000000"), "Amount", "1E+100000000");
    assertSafeFailure(() -> codec.encode(field, "1E-100000000"), "Amount", "1E-100000000");
    assertSafeFailure(() -> codec.encode(field, "1234567890123456"), "Amount", "1234567890123456");
    assertSafeFailure(() -> codec.encode(field, "0.12345678901"), "Amount", "0.12345678901");
    assertSafeFailure(() -> codec.encode(field, Double.NaN), "Amount", "NaN");
    assertSafeFailure(() -> codec.encode(field, Float.POSITIVE_INFINITY), "Amount", "Infinity");
  }

  @Test
  void encodesCheckboxOnlyFromDocumentedSimpleValues() {
    WecomSmartSheetField field = field("Enabled", "FIELD_TYPE_CHECKBOX", Map.of(), false);
    assertThat(codec.encode(field, "true").booleanValue()).isTrue();
    assertThat(codec.decode(field, JSON.getNodeFactory().booleanNode(false))).isEqualTo("false");
    assertThatThrownBy(() -> codec.encode(field, "yes-secret"))
        .hasMessageContaining("Enabled").hasMessageNotContaining("yes-secret");
  }

  @Test
  void convertsDateOnlyAndDateTimeInShanghaiAndSupportsEmptyClear() {
    WecomSmartSheetField day = field("Day", "FIELD_TYPE_DATE_TIME", Map.of(), false);
    WecomSmartSheetField moment = field("Moment", "FIELD_TYPE_DATE_TIME", Map.of(), true);
    assertThat(codec.encode(day, "2026-07-24").asText()).isEqualTo("1784822400000");
    assertThat(codec.decode(day, JSON.getNodeFactory().textNode("1784822400000"))).isEqualTo("2026-07-24");
    assertThat(codec.encode(moment, "2026-07-24T12:30:00").asText()).isEqualTo("1784867400000");
    assertThat(codec.decode(moment, JSON.getNodeFactory().textNode("1784867400000"))).isEqualTo("2026-07-24T12:30:00");
    assertThat(codec.encode(moment, "2026-07-24T12:30:00.123").asText()).isEqualTo("1784867400123");
    assertThat(codec.decode(moment, JSON.getNodeFactory().textNode("1784867400123"))).isEqualTo("2026-07-24T12:30:00.123");
    assertThat(codec.encode(day, "").asText()).isEmpty();
    assertThatThrownBy(() -> codec.encode(day, LocalDateTime.of(2026, 7, 24, 12, 30)))
        .hasMessageContaining("Day");
    assertThatThrownBy(() -> codec.encode(day, "2026-07-24T12:30:00"))
        .hasMessageContaining("Day");
    assertThat(codec.encode(moment, LocalDate.of(2026, 7, 24)).asText()).isEqualTo("1784822400000");
    assertThatThrownBy(() -> codec.encode(moment, LocalDateTime.of(2026, 7, 24, 12, 30, 0, 123_456_789)))
        .hasMessageContaining("Moment");
  }

  @Test
  void mapsExistingSelectIdsInStableOrderAndRejectsUnknowns() throws Exception {
    Map<String, String> options = Map.of("Gold", "o-gold", "Silver", "o-silver");
    WecomSmartSheetField single = field("Tier", "FIELD_TYPE_SINGLE_SELECT", options, false);
    WecomSmartSheetField multi = field("Tags", "FIELD_TYPE_SELECT", options, false);
    assertThat(codec.encode(single, " Gold ").get(0).path("id").asText()).isEqualTo("o-gold");
    assertThat(codec.encode(multi, List.of("Silver", "Gold", "Silver")).toString())
        .isEqualTo("[{\"id\":\"o-silver\"},{\"id\":\"o-gold\"}]");
    assertThat(codec.decode(multi, JSON.readTree("[{\"id\":\"o-silver\",\"text\":\"Silver\"},{\"id\":\"o-gold\",\"text\":\"Gold\"}]"))).isEqualTo("Silver、Gold");
    assertThat(codec.encode(multi, "")).isEmpty();
    assertThatThrownBy(() -> codec.encode(single, "secret-option"))
        .hasMessageContaining("Tier").hasMessageNotContaining("secret-option");
  }

  @Test
  void rejectsReadOnlyWritesAndSafelyReadsUnsupportedNodes() throws Exception {
    WecomSmartSheetField formula = field("Formula", "FIELD_TYPE_FORMULA", Map.of(), false);
    assertThatThrownBy(() -> codec.encode(formula, "secret-value"))
        .hasMessageContaining("Formula").hasMessageNotContaining("secret-value");
    JsonNode complex = JSON.readTree("{\"private\":\"value\",\"n\":1}");
    assertThat(codec.decode(formula, complex)).isEqualTo("{\"private\":\"value\",\"n\":1}");
    assertThatThrownBy(() -> codec.encode(field("Text", "FIELD_TYPE_TEXT", Map.of(), false), null))
        .hasMessageContaining("Text");
  }

  @Test
  void rejectsArbitraryObjectsAndCustomNumbersWithoutLeakingTheirValues() {
    String pii = "pii-to-string-13900000000";
    PiiValue value = new PiiValue(pii);
    PiiNumber number = new PiiNumber(pii);
    assertSafeFailure(() -> codec.encode(field("Text", "FIELD_TYPE_TEXT", Map.of(), false), value), "Text", pii);
    assertSafeFailure(() -> codec.encode(field("Phone", "FIELD_TYPE_PHONE_NUMBER", Map.of(), false), value), "Phone", pii);
    assertSafeFailure(() -> codec.encode(field("Email", "FIELD_TYPE_EMAIL", Map.of(), false), value), "Email", pii);
    assertSafeFailure(() -> codec.encode(field("Tags", "FIELD_TYPE_SELECT", Map.of("A", "o1"), false), List.of(value)), "Tags", pii);
    assertSafeFailure(() -> codec.encode(field("Amount", "FIELD_TYPE_NUMBER", Map.of(), false), number), "Amount", pii);
    assertSafeFailure(() -> codec.encode(field("Day", "FIELD_TYPE_DATE_TIME", Map.of(), false), value), "Day", pii);
  }

  private static void assertSafeFailure(Runnable action, String title, String pii) {
    assertThatThrownBy(action::run).satisfies(error -> {
      assertThat(error).hasMessageContaining(title).hasMessageNotContaining(pii);
      StringWriter output = new StringWriter();
      error.printStackTrace(new PrintWriter(output));
      assertThat(output.toString()).doesNotContain(pii);
    });
  }

  private static WecomSmartSheetField field(String title, String type, Map<String, String> options, boolean includesTime) {
    return new WecomSmartSheetField("f-" + title, title, type, options, includesTime);
  }

  private static WecomSmartSheetConfig config() {
    return new WecomSmartSheetConfig("http://127.0.0.1", "corp", "secret", "doc", "sheet", "view",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }

  private static final class PiiValue {
    private final String pii;
    private PiiValue(String pii) { this.pii = pii; }
    @Override public String toString() { throw new IllegalStateException(pii); }
  }

  private static final class PiiNumber extends Number {
    private final String pii;
    private PiiNumber(String pii) { this.pii = pii; }
    @Override public int intValue() { return 1; }
    @Override public long longValue() { return 1; }
    @Override public float floatValue() { return 1; }
    @Override public double doubleValue() { return 1; }
    @Override public String toString() { return pii; }
  }
}
