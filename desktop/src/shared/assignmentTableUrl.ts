const ALLOWED_ASSIGNMENT_TABLE_HOSTS = new Set(['doc.weixin.qq.com', 'work.weixin.qq.com']);

export function resolveAssignmentTableUrl(rawUrl?: string): string {
  const value = String(rawUrl ?? '').trim();
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || !ALLOWED_ASSIGNMENT_TABLE_HOSTS.has(url.hostname.toLowerCase())) {
      throw new Error('企业微信表格链接无效');
    }
    return url.toString();
  } catch (error) {
    if (error instanceof Error && error.message === '企业微信表格链接无效') throw error;
    throw new Error('企业微信表格链接无效');
  }
}
