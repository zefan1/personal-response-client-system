import { describe, expect, it } from 'vitest';
import {
  candidatePhone,
  isSameFullPhone,
  isSamePhone,
  normalizeFullPhone,
  profilePhone,
  summaryPhone
} from './customerPhone';

describe('customerPhone', () => {
  it('normalizes full Chinese mobile phone numbers and rejects masked values', () => {
    expect(normalizeFullPhone('+86 138-0000-0000')).toBe('13800000000');
    expect(normalizeFullPhone('138****0000')).toBe('');
    expect(normalizeFullPhone('12345')).toBe('');
  });

  it('uses full phone values when customer data provides them', () => {
    expect(summaryPhone({ phone: '****0000', phoneFull: '13800000000' })).toBe('13800000000');
    expect(candidatePhone({ phone: '13800000000' })).toBe('13800000000');
    expect(candidatePhone({ phone: '13800000000', phoneFull: null })).toBe('13800000000');
    expect(profilePhone({ phoneFull: '13800000000', customer: { phone: '****0000' } } as never)).toBe('13800000000');
  });

  it('matches only equal complete numbers and supports masked display comparisons', () => {
    expect(isSameFullPhone('+8613800000000', '13800000000')).toBe(true);
    expect(isSameFullPhone('138****0000', '13800000000')).toBe(false);
    expect(isSamePhone('13800000000', '****0000')).toBe(true);
    expect(isSamePhone('', '13800000000')).toBe(false);
  });
});
