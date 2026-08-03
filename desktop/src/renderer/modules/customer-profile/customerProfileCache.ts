const CACHE_PREFIX = 'customer_cache:';

type CachedCustomer<T> = {
  fullProfile: T;
  cachedAt: string;
  lastViewedAt: string;
};

export function saveCustomerCache<T>(storage: Storage, phone: string, fullProfile: T, timestamp: string): void {
  storage.setItem(`${CACHE_PREFIX}${phone}`, JSON.stringify({
    phone,
    fullProfile,
    cachedAt: timestamp,
    lastViewedAt: timestamp
  }));
}

export function loadCustomerCache<T>(storage: Storage, phone: string): CachedCustomer<T> | null {
  try {
    const raw = storage.getItem(`${CACHE_PREFIX}${phone}`);
    return raw ? JSON.parse(raw) as CachedCustomer<T> : null;
  } catch {
    return null;
  }
}

export function enforceCustomerCacheLimit(storage: Storage, limit: number): void {
  const items: Array<{ key: string; lastViewedAt: string }> = [];
  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index);
    if (!key?.startsWith(CACHE_PREFIX)) continue;
    const raw = storage.getItem(key);
    if (!raw) continue;
    try {
      items.push({ key, lastViewedAt: (JSON.parse(raw) as Partial<CachedCustomer<unknown>>).lastViewedAt ?? '' });
    } catch {
      storage.removeItem(key);
    }
  }
  items.sort((left, right) => left.lastViewedAt.localeCompare(right.lastViewedAt));
  items.slice(0, Math.max(0, items.length - limit)).forEach((item) => storage.removeItem(item.key));
}
