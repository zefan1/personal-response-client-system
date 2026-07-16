package com.privateflow.modules.skill;

public record ReplyTagSnapshot(
    String categoryKey,
    String categoryName,
    String tagValue,
    String tagDisplayName,
    String meaning,
    String sourceType,
    String evidenceText,
    boolean manualLocked
) {
}
