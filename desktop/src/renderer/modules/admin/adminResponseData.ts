export type AdminRecord = Record<string, any>;

const LIST_KEYS = [
  'items',
  'list',
  'records',
  'datasources',
  'fields',
  'mappings',
  'logs',
  'rules',
  'categories',
  'versions',
  'notices',
  'staff',
  'sources',
  'stages',
  'ranking',
  'actions',
  'columns',
  'recentAlerts',
  'systemAlerts'
] as const;

export function asAdminRecord(data: unknown): AdminRecord {
  return data && !Array.isArray(data) && typeof data === 'object' ? data as AdminRecord : {};
}

export function listFromData(value: unknown, preferredKey?: string): AdminRecord[] {
  const data = value as AdminRecord;
  if (Array.isArray(value)) return value as AdminRecord[];
  if (preferredKey && Array.isArray(data?.[preferredKey])) return data[preferredKey];
  for (const key of LIST_KEYS) {
    if (Array.isArray(data?.[key])) return data[key];
  }
  return [];
}

export function configEntriesFromData(data: unknown): AdminRecord[] {
  if (Array.isArray(data)) return data as AdminRecord[];
  const record = asAdminRecord(data);
  const nested = listFromData(record);
  if (nested.length) return nested;
  return Object.entries(record).map(([key, value]) => ({ key, configKey: key, value }));
}
