import { describe, expect, it } from 'vitest';
import { nextAdminPage } from './adminPageNavigation';

describe('nextAdminPage', () => {
  it('moves within the available page range', () => {
    expect(nextAdminPage(2, 5, -1)).toBe(1);
    expect(nextAdminPage(2, 5, 1)).toBe(3);
  });

  it('keeps the page at the nearest boundary', () => {
    expect(nextAdminPage(1, 5, -1)).toBe(1);
    expect(nextAdminPage(5, 5, 1)).toBe(5);
    expect(nextAdminPage(1, 0, 1)).toBe(1);
  });
});
