import { describe, expect, it } from 'vitest';
import { normalizeAdminPage } from './adminPagination';

describe('normalizeAdminPage', () => {
  it('preserves page-size key priority and server-supplied page totals', () => {
    expect(normalizeAdminPage(
      { total: 45, page: 2, size: 10, pageSize: 20, totalPages: 3 },
      0,
      { page: 1, size: 20, total: 0, totalPages: 1 },
      ['pageSize', 'size']
    )).toEqual({ total: 45, page: 2, size: 20, totalPages: 3 });
  });

  it('uses the rendered item count and calculated page total for partial responses', () => {
    expect(normalizeAdminPage(
      { page: 3 },
      5,
      { page: 1, size: 2, total: 0, totalPages: 1 },
      ['size', 'pageSize']
    )).toEqual({ total: 5, page: 3, size: 2, totalPages: 3 });
  });
});
