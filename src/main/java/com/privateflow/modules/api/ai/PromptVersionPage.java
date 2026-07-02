package com.privateflow.modules.api.ai;

import java.util.List;

public record PromptVersionPage(
    String configKey,
    int currentVersion,
    List<PromptVersion> versions
) {
}
