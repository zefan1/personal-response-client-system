package com.privateflow.modules.customer.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppointmentMessageParserTest {

  private final AppointmentMessageParser parser = new AppointmentMessageParser(
      Clock.fixed(Instant.parse("2026-08-24T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

  @Test
  void parsesExplicitAppointmentBlockAndNextWeekdayTime() {
    var result = parser.parse(List.of(new CustomerMessageSentEvent.ChatMessage(
        "customer",
        "预约人：张丹山\n预约时间：周三下午2点\n预约门店：立心科学产康（虎门店）\n预约项目：产康评估",
        null)), null);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().personName()).isEqualTo("张丹山");
    assertThat(result.orElseThrow().date()).isEqualTo("2026-08-26");
    assertThat(result.orElseThrow().time()).isEqualTo("14:00");
    assertThat(result.orElseThrow().store()).isEqualTo("立心科学产康（虎门店）");
    assertThat(result.orElseThrow().projects()).containsExactly("产康评估");
  }

  @Test
  void splitsMultipleProjectsIntoSeparateTasks() {
    var result = parser.parse(List.of(new CustomerMessageSentEvent.ChatMessage(
        "keeper",
        "预约日期：2026-08-31\n预约时间：10:30\n预约门店：东城店\n预约项目：孕按、通乳和产康",
        null)), null);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().personName()).isEmpty();
    assertThat(result.orElseThrow().projects()).containsExactly("孕按", "通乳", "产康");
  }

  @Test
  void doesNotTreatIncompleteConversationAsAppointment() {
    var result = parser.parse(List.of(new CustomerMessageSentEvent.ChatMessage(
        "customer", "周三下午2点，评估收费吗？", null)), null);

    assertThat(result).isEmpty();
  }
}
