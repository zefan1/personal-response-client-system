package com.privateflow.modules.match;

import com.privateflow.modules.profile.CustomerProfileView;
import java.util.List;

public record CustomerBatchResponse(List<CustomerProfileView> customers) {
}
