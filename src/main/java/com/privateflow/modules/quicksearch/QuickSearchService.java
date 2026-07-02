package com.privateflow.modules.quicksearch;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuickSearchService {

  private final QuickSearchRepository repository;

  public QuickSearchService(QuickSearchRepository repository) {
    this.repository = repository;
  }

  public List<QuickSearchItem> listEnabledItems() {
    return repository.findEnabledItems();
  }
}
