package com.privateflow.modules.profile;

import com.privateflow.modules.customer.Customer;
import java.util.List;

public record CustomerProfileView(Customer customer, List<ProfileSuggestion> pendingSuggestions) {
}
