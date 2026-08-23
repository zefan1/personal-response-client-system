package com.privateflow.modules.followup;

import java.util.List;

public record FollowupTodayResponse(
    String keeperId,
    int totalCount,
    List<FollowupItem> items,
    int pendingNewLeadCount,
    int retainedNewLeadCount) {

  public FollowupTodayResponse(String keeperId, int totalCount, List<FollowupItem> items) {
    this(keeperId, totalCount, items,
        (int) items.stream().filter(item -> item.reminderType() == ReminderType.NEW_LEAD && !item.leadProcessed()).count(),
        (int) items.stream().filter(item -> item.reminderType() == ReminderType.NEW_LEAD && item.leadProcessed()).count());
  }
}
