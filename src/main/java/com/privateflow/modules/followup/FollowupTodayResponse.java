package com.privateflow.modules.followup;

import java.util.List;

public record FollowupTodayResponse(String keeperId, int totalCount, List<FollowupItem> items) {
}
