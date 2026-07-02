package com.privateflow.modules.match;

public record MatchRequest(
    String nickname,
    String phone,
    String leadType,
    String sourceTable,
    String currentUser
) {
}
