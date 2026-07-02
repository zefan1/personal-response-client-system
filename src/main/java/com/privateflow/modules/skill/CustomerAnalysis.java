package com.privateflow.modules.skill;

public record CustomerAnalysis(
    String intent,
    String emotion,
    String personalityTypeSuggest,
    String confidence
) {
}
