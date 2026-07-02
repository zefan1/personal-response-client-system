package com.privateflow.modules.quicksearch;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuickSearchController {

  private final QuickSearchService service;

  public QuickSearchController(QuickSearchService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/quick-search/items")
  public ApiResponse<List<QuickSearchItem>> listItems() {
    return ApiResponse.ok(service.listEnabledItems());
  }
}
