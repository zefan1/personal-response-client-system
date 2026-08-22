package com.privateflow.modules.customer.admin;

import java.util.List;

public record CustomerMasterRecord(CustomerMasterCandidate customer, List<CustomerMasterFieldValue> fields) {
}
