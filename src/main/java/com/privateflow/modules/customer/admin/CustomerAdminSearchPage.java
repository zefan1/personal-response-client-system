package com.privateflow.modules.customer.admin;

import java.util.List;

public record CustomerAdminSearchPage(
    List<CustomerAdminListItem> items,
    long total,
    int page,
    int size,
    int totalPages) {
}
