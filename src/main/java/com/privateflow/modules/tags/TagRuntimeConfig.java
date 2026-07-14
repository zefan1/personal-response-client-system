package com.privateflow.modules.tags;

public record TagRuntimeConfig(
    int cacheRefreshIntervalSeconds,
    int valueMaxPerCategory
) {
}
