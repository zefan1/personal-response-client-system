package com.privateflow.modules.profile;

import java.util.List;

public record BatchResolveRequest(ResolveAction action, List<Long> suggestionIds, String operator) {
}
