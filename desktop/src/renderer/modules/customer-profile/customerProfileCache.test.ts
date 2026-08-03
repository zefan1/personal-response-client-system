import { describe, expect, it } from 'vitest';
import { enforceCustomerCacheLimit, loadCustomerCache, saveCustomerCache } from './customerProfileCache';

function storage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() { return values.size; },
    key: (index: number) => [...values.keys()][index] ?? null,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear()
  } as Storage;
}

describe('customerProfileCache', () => {
  it('stores and restores a customer profile by complete phone number', () => {
    const local = storage();
    saveCustomerCache(local, '13800000000', { customer: { phone: '13800000000' } }, '2026-07-27T10:00:00.000Z');

    expect(loadCustomerCache<{ customer: { phone: string } }>(local, '13800000000')).toMatchObject({
      cachedAt: '2026-07-27T10:00:00.000Z',
      fullProfile: { customer: { phone: '13800000000' } }
    });
  });

  it('removes malformed and oldest entries when the cache exceeds its limit', () => {
    const local = storage();
    saveCustomerCache(local, '13800000001', {}, '2026-07-27T08:00:00.000Z');
    saveCustomerCache(local, '13800000002', {}, '2026-07-27T09:00:00.000Z');
    local.setItem('customer_cache:broken', '{invalid');

    enforceCustomerCacheLimit(local, 1);

    expect(loadCustomerCache(local, '13800000001')).toBeNull();
    expect(loadCustomerCache(local, '13800000002')).not.toBeNull();
    expect(local.getItem('customer_cache:broken')).toBeNull();
  });
});
