package com.privateflow.modules.llm;

public record FollowupAnalysisPayload(
    String internalNote,
    String bodyConcerns,
    String customerProfileSummary,
    String followupRecord,
    String customerStage,
    String nextFollowupDirection,
    String nextFollowupAt,
    String trackingCapture
) {
}
