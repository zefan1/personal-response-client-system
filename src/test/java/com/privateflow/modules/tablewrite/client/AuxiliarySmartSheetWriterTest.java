package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuxiliarySmartSheetWriterTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final AuxiliarySmartSheetTarget TARGET = new AuxiliarySmartSheetTarget(
      "ARRIVAL", "doc-1", "sheet-1", "view-1", "手机号", "");

  @Test
  void treatsAnEmptyTargetedReadAsADeletedRecord() throws Exception {
    WecomSmartSheetApiClient api = mock(WecomSmartSheetApiClient.class);
    when(api.postForTarget(eq("get_records"), any(), eq(TIMEOUT), eq(false)))
        .thenReturn(new ObjectMapper().readTree("{\"errcode\":0,\"records\":[]}"));

    boolean exists = new AuxiliarySmartSheetWriter(api, mock(WecomSmartSheetValueCodec.class))
        .recordExists(TARGET, "old-row", TIMEOUT);

    assertThat(exists).isFalse();
  }

  @Test
  void confirmsTheNamedRecordWhenTargetedReadReturnsIt() throws Exception {
    WecomSmartSheetApiClient api = mock(WecomSmartSheetApiClient.class);
    when(api.postForTarget(eq("get_records"), any(), eq(TIMEOUT), eq(false)))
        .thenReturn(new ObjectMapper().readTree("""
            {"errcode":0,"records":[{"record_id":"current-row","values":{}}]}
            """));

    boolean exists = new AuxiliarySmartSheetWriter(api, mock(WecomSmartSheetValueCodec.class))
        .recordExists(TARGET, "current-row", TIMEOUT);

    assertThat(exists).isTrue();
  }
}
