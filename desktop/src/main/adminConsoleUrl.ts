export type AdminConsoleUrlOptions = {
  requestedUrl?: string;
  configuredUrl?: string;
  devServerUrl?: string;
  isDev: boolean;
};

export function resolveAdminConsoleUrl({
  requestedUrl,
  configuredUrl,
  devServerUrl,
  isDev
}: AdminConsoleUrlOptions): string {
  const base = isDev && requestedUrl?.trim()
    ? requestedUrl.trim()
    : configuredUrl?.trim()
      ? configuredUrl.trim()
      : isDev && devServerUrl?.trim()
        ? `${devServerUrl.trim()}/#/admin`
        : '';
  if (!base) {
    throw new Error('PDA_ADMIN_CONSOLE_URL is required to open admin console in packaged builds');
  }
  const url = new URL(base);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('Admin console URL must use http or https');
  }
  url.hash = '#/admin';
  return url.toString();
}
