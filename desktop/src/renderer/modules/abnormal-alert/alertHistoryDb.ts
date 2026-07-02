import type { AbnormalAlert } from './types';

const DB_NAME = 'siliang_desktop';
const DB_VERSION = 4;
const STORE_NAME = 'alert_history';

let dbPromise: Promise<IDBDatabase> | null = null;

export async function insertAlertHistory(alert: AbnormalAlert, maxCount: number): Promise<void> {
  const db = await openAlertDatabase();
  await runTransaction(db, 'readwrite', async (store) => {
    const count = await requestToPromise(store.count());
    if (count >= maxCount) {
      const oldest = await cursorToPromise(store.index('occurredAt').openCursor(null, 'next'));
      oldest?.delete();
    }
    store.put(alert);
  });
}

export async function updateAlertAcknowledged(alert: AbnormalAlert): Promise<void> {
  const db = await openAlertDatabase();
  await runTransaction(db, 'readwrite', async (store) => {
    store.put(alert);
  });
}

export async function getAlertsByPhone(phone: string): Promise<AbnormalAlert[]> {
  const db = await openAlertDatabase();
  return runTransaction(db, 'readonly', async (store) => {
    const alerts = await requestToPromise<AbnormalAlert[]>(store.index('phone').getAll(phone));
    return sortByOccurredAtDesc(alerts);
  });
}

export async function getRecentAlerts(limit = 10): Promise<AbnormalAlert[]> {
  const db = await openAlertDatabase();
  return runTransaction(db, 'readonly', async (store) => {
    const alerts: AbnormalAlert[] = [];
    const index = store.index('occurredAt');
    let cursor = await cursorToPromise(index.openCursor(null, 'prev'));
    while (cursor && alerts.length < limit) {
      alerts.push(cursor.value as AbnormalAlert);
      cursor = await continueCursor(cursor);
    }
    return alerts;
  });
}

export async function deleteExpiredAlerts(retentionDays: number): Promise<void> {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - retentionDays);
  const db = await openAlertDatabase();
  await runTransaction(db, 'readwrite', async (store) => {
    const range = IDBKeyRange.upperBound(cutoff.toISOString());
    let cursor = await cursorToPromise(store.index('occurredAt').openCursor(range, 'next'));
    while (cursor) {
      cursor.delete();
      cursor = await continueCursor(cursor);
    }
  });
}

export function closeAlertDatabase(): void {
  void dbPromise?.then((db) => db.close());
  dbPromise = null;
}

function openAlertDatabase(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'alertId' });
          store.createIndex('phone', 'phone', { unique: false });
          store.createIndex('occurredAt', 'occurredAt', { unique: false });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error ?? new Error('K_INDEXEDDB_OPEN_FAILED'));
      request.onblocked = () => reject(new Error('K_INDEXEDDB_OPEN_BLOCKED'));
    });
  }
  return dbPromise;
}

function runTransaction<T>(
  db: IDBDatabase,
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => Promise<T>
): Promise<T> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, mode);
    const store = tx.objectStore(STORE_NAME);
    let value: T;
    tx.oncomplete = () => resolve(value);
    tx.onerror = () => reject(tx.error ?? new Error('K_INDEXEDDB_TRANSACTION_FAILED'));
    tx.onabort = () => reject(tx.error ?? new Error('K_INDEXEDDB_TRANSACTION_ABORTED'));
    operation(store).then((result) => {
      value = result;
    }).catch((error) => {
      tx.abort();
      reject(error);
    });
  });
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('K_INDEXEDDB_REQUEST_FAILED'));
  });
}

function cursorToPromise(request: IDBRequest<IDBCursorWithValue | null>): Promise<IDBCursorWithValue | null> {
  return requestToPromise(request);
}

function continueCursor(cursor: IDBCursorWithValue): Promise<IDBCursorWithValue | null> {
  cursor.continue();
  return cursorToPromise(cursor.request as IDBRequest<IDBCursorWithValue | null>);
}

function sortByOccurredAtDesc(alerts: AbnormalAlert[]): AbnormalAlert[] {
  return [...alerts].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
}
