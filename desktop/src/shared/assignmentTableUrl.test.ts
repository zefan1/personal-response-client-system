import { describe, expect, it } from 'vitest';
import { resolveAssignmentTableUrl } from './assignmentTableUrl.js';

describe('resolveAssignmentTableUrl', () => {
  it('accepts only HTTPS WeCom document hosts', () => {
    expect(resolveAssignmentTableUrl('https://doc.weixin.qq.com/sheet/abc')).toBe('https://doc.weixin.qq.com/sheet/abc');
    expect(resolveAssignmentTableUrl('https://work.weixin.qq.com/ca/cawcde')).toBe('https://work.weixin.qq.com/ca/cawcde');
  });

  it.each([
    'http://doc.weixin.qq.com/sheet/abc',
    'https://example.com/sheet/abc',
    'javascript:alert(1)',
    ''
  ])('rejects unsafe or empty links: %s', (url) => {
    expect(() => resolveAssignmentTableUrl(url)).toThrow('企业微信表格链接无效');
  });
});
