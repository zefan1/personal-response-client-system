package com.privateflow.modules.match;

import java.util.List;

public record CustomerSearchResult(List<CustomerSummary> customers, int total) {
}
