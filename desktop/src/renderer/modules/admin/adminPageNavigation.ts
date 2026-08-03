export function nextAdminPage(currentPage: number, totalPages: number, delta: number): number {
  const maxPage = Math.max(1, totalPages);
  return Math.min(maxPage, Math.max(1, currentPage + delta));
}
