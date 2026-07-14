package com.privateflow.modules.tags;

public record TagMergeRequest(
    Long targetId,
    Integer sourceVersion,
    Integer targetVersion
) {
}
