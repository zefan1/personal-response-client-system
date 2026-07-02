package com.privateflow.modules.match.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.config.MatchConfigProvider;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FuzzyMatcher {

  private static final Logger log = LoggerFactory.getLogger(FuzzyMatcher.class);
  private final CustomerQueryService customerQueryService;
  private final MatchConfigProvider configProvider;

  public FuzzyMatcher(CustomerQueryService customerQueryService, MatchConfigProvider configProvider) {
    this.customerQueryService = customerQueryService;
    this.configProvider = configProvider;
  }

  public List<Customer> matchByNickname(String cleanedNickname) {
    CompletableFuture<List<Customer>> future = CompletableFuture.supplyAsync(() ->
        customerQueryService.searchByNickname(cleanedNickname, configProvider.get().maxCandidates()));
    try {
      return future.get(configProvider.get().fuzzySearchTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      future.cancel(true);
      log.warn("customer fuzzy match timeout, nickname={}", cleanedNickname);
      return List.of();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (ExecutionException ex) {
      throw new CustomerMatchException(
          CustomerMatchErrorCodes.MATCH_FAILED,
          "客户匹配服务暂不可用",
          ex.getCause());
    }
  }
}
