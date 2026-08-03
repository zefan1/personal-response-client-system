package com.privateflow.modules.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunicationDeduplicationServiceTest {

  private final CommunicationDeduplicationService service =
      new CommunicationDeduplicationService();

  @Test
  void removesTheLargestOverlappingConversationPrefix() {
    List<CommunicationMessageDraft> existing = List.of(
        message("CUSTOMER", "生完宝宝三个月了", "2026-08-01T10:02:00", false),
        message("EMPLOYEE", "之前有做过产后检查吗？", "2026-08-01T10:03:00", false));
    List<CommunicationMessageDraft> incoming = List.of(
        message("CUSTOMER", "生完宝宝三个月了", "2026-08-01T10:02:00", false),
        message("EMPLOYEE", "之前有做过产后检查吗？", "2026-08-01T10:03:00", false),
        message("CUSTOMER", "没有，而且最近一直腰痛", "2026-08-01T10:05:00", false));

    assertThat(service.removeOverlappingPrefix(existing, incoming))
        .extracting(CommunicationMessageDraft::text)
        .containsExactly("没有，而且最近一直腰痛");
  }

  @Test
  void estimatedTimesCanDeduplicateOnlyWhenAdjacentContextAlsoMatches() {
    List<CommunicationMessageDraft> existing = List.of(
        message("CUSTOMER", "好的", "2026-08-01T10:00:00", true),
        message("EMPLOYEE", "明天下午联系您", "2026-08-01T10:00:01", true));
    List<CommunicationMessageDraft> incoming = List.of(
        message("CUSTOMER", "好的", "2026-08-01T10:05:00", true),
        message("EMPLOYEE", "明天下午联系您", "2026-08-01T10:05:01", true),
        message("CUSTOMER", "谢谢", "2026-08-01T10:05:02", true));

    assertThat(service.removeOverlappingPrefix(existing, incoming))
        .extracting(CommunicationMessageDraft::text)
        .containsExactly("谢谢");
  }

  @Test
  void preservesSameTextSentAgainAtAnotherRealTime() {
    List<CommunicationMessageDraft> existing = List.of(
        message("CUSTOMER", "好的", "2026-08-01T10:00:00", false));
    List<CommunicationMessageDraft> incoming = List.of(
        message("CUSTOMER", "好的", "2026-08-01T11:00:00", false));

    assertThat(service.removeOverlappingPrefix(existing, incoming))
        .extracting(CommunicationMessageDraft::text)
        .containsExactly("好的");
  }

  private CommunicationMessageDraft message(
      String role,
      String text,
      String time,
      boolean estimated) {
    return new CommunicationMessageDraft(
        role,
        text,
        "TEXT",
        LocalDateTime.parse(time),
        estimated);
  }
}
