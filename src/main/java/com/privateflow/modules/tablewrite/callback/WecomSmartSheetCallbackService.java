package com.privateflow.modules.tablewrite.callback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetRecordClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WecomSmartSheetCallbackService {

  private static final TypeReference<List<String>> RECORD_IDS = new TypeReference<>() {};
  private final WecomCallbackCrypto crypto;
  private final SmartSheetCallbackTargetResolver targetResolver;
  private final SmartSheetCallbackInboxRepository inboxRepository;
  private final WecomSmartSheetRecordClient recordClient;
  private final SmartSheetCallbackRowApplier rowApplier;
  private final ObjectMapper objectMapper;

  public WecomSmartSheetCallbackService(
      WecomSmartSheetCallbackConfig config,
      SmartSheetCallbackTargetResolver targetResolver,
      SmartSheetCallbackInboxRepository inboxRepository,
      WecomSmartSheetRecordClient recordClient,
      SmartSheetCallbackRowApplier rowApplier,
      ObjectMapper objectMapper) {
    this.crypto = new WecomCallbackCrypto(config);
    this.targetResolver = targetResolver;
    this.inboxRepository = inboxRepository;
    this.recordClient = recordClient;
    this.rowApplier = rowApplier;
    this.objectMapper = objectMapper;
  }

  public String verifyChallenge(String signature, String timestamp, String nonce, String encryptedEcho) {
    return crypto.decrypt(signature, timestamp, nonce, encryptedEcho);
  }

  public void receive(String signature, String timestamp, String nonce, String encryptedXml) {
    String encrypted = crypto.encryptedValue(encryptedXml);
    String payload = crypto.decrypt(signature, timestamp, nonce, encrypted);
    XmlValues values = XmlValues.parse(payload);
    if (!"event".equals(values.first("MsgType")) || !"smart_sheet_change".equals(values.first("Event"))) {
      return;
    }
    String changeType = values.first("ChangeType");
    if (!List.of("add_record", "update_record", "delete_record").contains(changeType)) {
      return;
    }
    List<String> recordIds = values.all("RecordId").stream()
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .sorted()
        .toList();
    if (recordIds.isEmpty()) {
      return;
    }
    String documentId = values.first("DocId");
    String sheetId = values.first("SheetId");
    targetResolver.resolve(documentId, sheetId).ifPresent(target -> {
      SmartSheetCallbackEvent event = new SmartSheetCallbackEvent(
          eventKey(documentId, sheetId, changeType, values.first("CreateTime"), recordIds),
          target.role(), target.sourceTable(), target.target(), changeType, recordIds,
          values.first("FromUserName"));
      try {
        inboxRepository.enqueue(event, objectMapper.writeValueAsString(recordIds));
      } catch (Exception ex) {
        throw new IllegalStateException("WeCom callback could not be queued");
      }
    });
  }

  /** Accepts an already authenticated, durable event claimed from the server-side relay. */
  void receiveRelayed(WecomInboundCallbackRelayClient.RelayEvent event) {
    if (event == null || !List.of("add_record", "update_record", "delete_record").contains(event.change_type())) {
      return;
    }
    List<String> recordIds = event.record_ids() == null ? List.of() : event.record_ids().stream()
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .sorted()
        .toList();
    if (recordIds.isEmpty() || event.event_key() == null || event.event_key().isBlank()) {
      return;
    }
    targetResolver.resolve(event.document_id(), event.sheet_id()).ifPresent(target -> {
      SmartSheetCallbackEvent localEvent = new SmartSheetCallbackEvent(
          event.event_key(), target.role(), target.sourceTable(), target.target(), event.change_type(), recordIds,
          event.operator_name());
      try {
        inboxRepository.enqueue(localEvent, objectMapper.writeValueAsString(recordIds));
      } catch (Exception ex) {
        throw new IllegalStateException("relayed WeCom callback could not be queued");
      }
    });
  }

  @Scheduled(fixedDelayString = "${wecom.callback.process-interval-ms:5000}")
  public void processDueCallbacks() {
    inboxRepository.recoverStaleProcessing();
    for (SmartSheetCallbackInboxRepository.InboxItem item : inboxRepository.claimDue(100)) {
      process(item);
    }
  }

  private void process(SmartSheetCallbackInboxRepository.InboxItem item) {
    if ("delete_record".equals(item.changeType())) {
      inboxRepository.ignore(item.id(), "source row deletion never deletes a customer automatically");
      return;
    }
    try {
      SmartSheetCallbackTargetResolver.ResolvedTarget target = targetResolver
          .resolve(item.documentId(), item.sheetId())
          .orElseThrow(() -> new IllegalStateException("callback target is no longer configured"));
      List<String> recordIds = objectMapper.readValue(item.recordIdsJson(), RECORD_IDS);
      List<SheetRow> rows = recordClient.fetchRecords(target.target(), recordIds, Duration.ofSeconds(20));
      for (SheetRow row : rows) {
        rowApplier.apply(target, row, item.operator());
      }
      inboxRepository.resolve(item.id());
    } catch (Exception ex) {
      long seconds = Math.min(300L, 5L * (1L << Math.min(5, Math.max(0, item.attempts()))));
      inboxRepository.defer(item.id(), ex.getMessage(), LocalDateTime.now().plusSeconds(seconds));
    }
  }

  private static String eventKey(
      String documentId, String sheetId, String changeType, String createdAt, List<String> recordIds) {
    try {
      String joined = String.join("|", documentId, sheetId, changeType, createdAt,
          recordIds.stream().sorted(Comparator.naturalOrder()).reduce("", (left, right) -> left + "," + right));
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("callback event key could not be generated");
    }
  }
}
