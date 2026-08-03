import { describe, expect, it } from 'vitest';
import { asAdminRecord, configEntriesFromData, listFromData } from './adminResponseData';

describe('adminResponseData', () => {
  it('selects an explicitly preferred list before generic response keys', () => {
    expect(listFromData({ items: [{ id: 'generic' }], details: [{ id: 'preferred' }] }, 'details')).toEqual([
      { id: 'preferred' }
    ]);
    expect(listFromData({ records: [{ id: 'record' }] })).toEqual([{ id: 'record' }]);
    expect(listFromData({ value: 'not-a-list' })).toEqual([]);
  });

  it('keeps only object response data as an admin record', () => {
    expect(asAdminRecord({ status: 'ok' })).toEqual({ status: 'ok' });
    expect(asAdminRecord([{ status: 'ok' }])).toEqual({});
    expect(asAdminRecord(null)).toEqual({});
  });

  it('uses nested lists or converts a configuration object into entries', () => {
    expect(configEntriesFromData({ items: [{ key: 'feature.enabled', value: 'true' }] })).toEqual([
      { key: 'feature.enabled', value: 'true' }
    ]);
    expect(configEntriesFromData({ 'feature.enabled': 'true' })).toEqual([
      { key: 'feature.enabled', configKey: 'feature.enabled', value: 'true' }
    ]);
  });
});
