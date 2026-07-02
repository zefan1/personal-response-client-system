package com.privateflow.modules.followup;

import java.util.List;

public record RulePage(int page, int size, long total, List<FollowupRule> items) {
}
