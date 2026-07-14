package com.privateflow.modules.tags;

import java.util.List;

public record TagMergePreview(
    String entityType,
    long sourceId,
    long targetId,
    String sourceCode,
    String sourceName,
    String targetCode,
    String targetName,
    TagImpact impact,
    int valueCount,
    int codeConflictCount,
    List<String> warnings
) {
}
