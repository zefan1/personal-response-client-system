const DB_NAME = 'cowork-desktop';
const DB_VERSION = 1;
const APP_CACHE_VERSION = '0.1.0';
const CACHE_VERSION_KEY = 'cowork_db_version';

type StoreSchema = {
  keyPath: string;
  autoIncrement?: boolean;
  indexes?: Array<{ name: string; keyPath: string }>;
};

const DB_SCHEMA: Record<string, StoreSchema> = {
  customers_cache: {
    keyPath: 'phone',
    indexes: [
      { name: 'lastViewedAt', keyPath: 'lastViewedAt' },
      { name: 'nickname', keyPath: 'nickname' }
    ]
  },
  quick_search_cache: {
    keyPath: 'id',
    indexes: [
      { name: 'contentType', keyPath: 'contentType' },
      { name: 'shortcutCode', keyPath: 'shortcutCode' },
      { name: 'leadType', keyPath: 'leadType' }
    ]
  },
  followups_cache: {
    keyPath: 'phone',
    indexes: [
      { name: 'reminderType', keyPath: 'reminderType' },
      { name: 'nextFollowupAt', keyPath: 'nextFollowupAt' }
    ]
  },
  pending_saves: {
    keyPath: 'phone',
    indexes: [{ name: 'createdAt', keyPath: 'createdAt' }]
  },
  alert_history: {
    keyPath: 'alertId',
    indexes: [
      { name: 'occurredAt', keyPath: 'occurredAt' },
      { name: 'phone', keyPath: 'phone' }
    ]
  },
  edit_logs: {
    keyPath: 'logId',
    autoIncrement: true,
    indexes: [
      { name: 'timestamp', keyPath: 'timestamp' },
      { name: 'phone', keyPath: 'phone' }
    ]
  },
  workbench_cache: {
    keyPath: 'cacheKey'
  }
};

let dbPromise: Promise<IDBDatabase> | null = null;

export async function initOfflineDatabase(): Promise<IDBDatabase> {
  if (dbPromise) {
    return dbPromise;
  }
  dbPromise = ensureCacheVersion().then(openDatabase);
  return dbPromise;
}

export function getOfflineDatabase(): Promise<IDBDatabase> {
  return initOfflineDatabase();
}

export async function resetOfflineDatabase(): Promise<IDBDatabase> {
  const current = await dbPromise?.catch(() => null);
  current?.close();
  dbPromise = null;
  await deleteDatabase();
  localStorage.setItem(CACHE_VERSION_KEY, APP_CACHE_VERSION);
  return initOfflineDatabase();
}

async function ensureCacheVersion(): Promise<void> {
  const storedVersion = localStorage.getItem(CACHE_VERSION_KEY);
  if (storedVersion === APP_CACHE_VERSION) {
    return;
  }
  await deleteDatabase();
  localStorage.setItem(CACHE_VERSION_KEY, APP_CACHE_VERSION);
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      Object.entries(DB_SCHEMA).forEach(([storeName, schema]) => {
        const store = db.objectStoreNames.contains(storeName)
          ? request.transaction?.objectStore(storeName)
          : db.createObjectStore(storeName, { keyPath: schema.keyPath, autoIncrement: schema.autoIncrement });
        schema.indexes?.forEach((index) => {
          if (store && !store.indexNames.contains(index.name)) {
            store.createIndex(index.name, index.keyPath);
          }
        });
      });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Failed to open offline database'));
  });
}

function deleteDatabase(): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(DB_NAME);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error ?? new Error('Failed to delete offline database'));
    request.onblocked = () => resolve();
  });
}

export { APP_CACHE_VERSION, CACHE_VERSION_KEY, DB_NAME, DB_SCHEMA, DB_VERSION };
