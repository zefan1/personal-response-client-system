export type AdminPage = {
  total: number;
  page: number;
  size: number;
  totalPages: number;
};

export function normalizeAdminPage(
  data: Record<string, unknown>,
  itemCount: number,
  current: AdminPage,
  sizeKeys: readonly string[]
): AdminPage {
  const total = numberValue(data.total, itemCount);
  const page = numberValue(data.page, current.page || 1);
  const size = firstNumber(data, sizeKeys, current.size || 20);
  const totalPages = numberValue(data.totalPages, Math.ceil(total / Math.max(1, size)) || 1);

  return { total, page, size, totalPages };
}

function firstNumber(data: Record<string, unknown>, keys: readonly string[], fallback: number): number {
  for (const key of keys) {
    if (data[key] !== undefined && data[key] !== null) return Number(data[key]);
  }
  return fallback;
}

function numberValue(value: unknown, fallback: number): number {
  return value === undefined || value === null ? fallback : Number(value);
}
