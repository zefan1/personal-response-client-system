package com.privateflow.modules.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache")
public class CustomerCacheProperties {
  private String syncCron = "0 */30 * * * *";
  private int ttlSeconds = 900;
  private int loadBatchSize = 500;
  private int syncTimeoutMs = 10000;
  private int maxSyncRowsPerRound = 10000;
  private int lockSpinMax = 3;
  private int lockSpinIntervalMs = 100;
  private int lockTtlS = 5;

  public String getSyncCron() { return syncCron; }
  public void setSyncCron(String syncCron) { this.syncCron = syncCron; }
  public int getTtlSeconds() { return ttlSeconds; }
  public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
  public int getLoadBatchSize() { return loadBatchSize; }
  public void setLoadBatchSize(int loadBatchSize) { this.loadBatchSize = loadBatchSize; }
  public int getSyncTimeoutMs() { return syncTimeoutMs; }
  public void setSyncTimeoutMs(int syncTimeoutMs) { this.syncTimeoutMs = syncTimeoutMs; }
  public int getMaxSyncRowsPerRound() { return maxSyncRowsPerRound; }
  public void setMaxSyncRowsPerRound(int maxSyncRowsPerRound) { this.maxSyncRowsPerRound = maxSyncRowsPerRound; }
  public int getLockSpinMax() { return lockSpinMax; }
  public void setLockSpinMax(int lockSpinMax) { this.lockSpinMax = lockSpinMax; }
  public int getLockSpinIntervalMs() { return lockSpinIntervalMs; }
  public void setLockSpinIntervalMs(int lockSpinIntervalMs) { this.lockSpinIntervalMs = lockSpinIntervalMs; }
  public int getLockTtlS() { return lockTtlS; }
  public void setLockTtlS(int lockTtlS) { this.lockTtlS = lockTtlS; }
}
