package com.privateflow.modules.communication;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CommunicationDeduplicationService {

  private static final Duration ESTIMATED_OVERLAP_WINDOW = Duration.ofMinutes(30);

  public List<CommunicationMessageDraft> removeOverlappingPrefix(
      List<CommunicationMessageDraft> existing,
      List<CommunicationMessageDraft> incoming) {
    List<CommunicationMessageDraft> safeExisting = existing == null ? List.of() : existing;
    List<CommunicationMessageDraft> safeIncoming = incoming == null ? List.of() : incoming;
    int maximum = Math.min(safeExisting.size(), safeIncoming.size());
    for (int overlap = maximum; overlap >= 1; overlap--) {
      if (overlap == 1 && !sameExactMessage(
          safeExisting.get(safeExisting.size() - 1), safeIncoming.get(0))) {
        continue;
      }
      boolean matches = true;
      for (int index = 0; index < overlap; index++) {
        CommunicationMessageDraft oldMessage =
            safeExisting.get(safeExisting.size() - overlap + index);
        CommunicationMessageDraft newMessage = safeIncoming.get(index);
        if (!sameMessage(oldMessage, newMessage)) {
          matches = false;
          break;
        }
      }
      if (matches) {
        return List.copyOf(safeIncoming.subList(overlap, safeIncoming.size()));
      }
    }
    return List.copyOf(safeIncoming);
  }

  private boolean sameMessage(
      CommunicationMessageDraft existing,
      CommunicationMessageDraft incoming) {
    if (existing == null || incoming == null
        || !normalize(existing.senderRole()).equals(normalize(incoming.senderRole()))
        || !normalize(existing.text()).equals(normalize(incoming.text()))
        || !normalize(existing.contentType()).equals(normalize(incoming.contentType()))
        || existing.messageTime() == null
        || incoming.messageTime() == null) {
      return false;
    }
    if (!existing.timeEstimated() && !incoming.timeEstimated()) {
      return existing.messageTime().equals(incoming.messageTime());
    }
    return Duration.between(existing.messageTime(), incoming.messageTime()).abs()
        .compareTo(ESTIMATED_OVERLAP_WINDOW) <= 0;
  }

  private boolean sameExactMessage(
      CommunicationMessageDraft existing,
      CommunicationMessageDraft incoming) {
    return sameMessage(existing, incoming)
        && !existing.timeEstimated()
        && !incoming.timeEstimated();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
  }
}
