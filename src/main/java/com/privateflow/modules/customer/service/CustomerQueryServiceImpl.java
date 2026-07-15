package com.privateflow.modules.customer.service;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.common.events.CustomerTagsUpdatedEvent;
import com.privateflow.common.events.ProfileUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.ScanFilter;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class CustomerQueryServiceImpl implements CustomerQueryService {

  private static final Logger log = LoggerFactory.getLogger(CustomerQueryServiceImpl.class);
  private final CustomerCacheManager cacheManager;
  private final CustomerRepository customerRepository;
  private final CustomerCacheProperties properties;
  private final int matchMaxCandidates;
  private final int followupScanBatchSize;

  public CustomerQueryServiceImpl(
      CustomerCacheManager cacheManager,
      CustomerRepository customerRepository,
      CustomerCacheProperties properties,
      @Value("${match.max-candidates:5}") int matchMaxCandidates,
      @Value("${followup.scan-batch-size:5000}") int followupScanBatchSize) {
    this.cacheManager = cacheManager;
    this.customerRepository = customerRepository;
    this.properties = properties;
    this.matchMaxCandidates = matchMaxCandidates;
    this.followupScanBatchSize = followupScanBatchSize;
  }

  @Override
  public Customer getByPhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    Optional<Customer> cached = cacheManager.read(phone);
    if (cached.isPresent()) {
      return cached.get();
    }
    Optional<String> token = cacheManager.tryLock(phone);
    if (token.isPresent()) {
      try {
        Customer loaded = customerRepository.findByPhone(phone).orElse(null);
        if (loaded != null) {
          cacheManager.write(loaded);
        }
        return loaded;
      } finally {
        cacheManager.unlock(phone, token.get());
      }
    }
    for (int i = 0; i < properties.getLockSpinMax(); i++) {
      sleep(properties.getLockSpinIntervalMs());
      cached = cacheManager.read(phone);
      if (cached.isPresent()) {
        return cached.get();
      }
    }
    return customerRepository.findByPhone(phone).orElse(null);
  }

  @Override
  public List<Customer> searchByNickname(String nickname) {
    return searchByNickname(nickname, matchMaxCandidates);
  }

  @Override
  public List<Customer> searchByNickname(String nickname, int limit) {
    if (nickname == null || nickname.isBlank()) {
      return List.of();
    }
    int actualLimit = Math.max(1, Math.min(limit, 50));
    return customerRepository.searchByNickname(nickname.trim(), actualLimit);
  }

  @Override
  public List<Customer> searchByKeyword(String keyword, int limit) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    int actualLimit = Math.max(1, Math.min(limit, 50));
    return customerRepository.searchByKeyword(keyword.trim(), actualLimit);
  }

  @Override
  public List<Customer> scanActiveCustomers(ScanFilter filter) {
    ScanFilter actual = filter == null ? new ScanFilter(null, null, null, null, followupScanBatchSize) : filter;
    return customerRepository.scanActiveCustomers(actual, followupScanBatchSize);
  }

  @Override
  public void refreshCache(String phone) {
    if (phone == null || phone.isBlank()) {
      return;
    }
    try {
      Customer loaded = customerRepository.findByPhone(phone).orElse(null);
      if (loaded == null) {
        cacheManager.evict(phone);
      } else {
        cacheManager.write(loaded);
      }
    } catch (RuntimeException ex) {
      cacheManager.evict(phone);
      log.warn("refresh customer cache failed, evicted stale cache, phone={}", phone);
    }
  }

  @EventListener
  public void onProfileUpdated(ProfileUpdatedEvent event) {
    refreshCache(event.phone());
  }

  @EventListener
  public void onCustomerTagsUpdated(CustomerTagsUpdatedEvent event) {
    refreshCache(event.phone());
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("cache.")) {
      log.info("module A received cache config change: {}", event.configKey());
    }
  }

  private void sleep(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
