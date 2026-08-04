export type SectionGroupKey = 'config-center' | 'data-content' | 'org-rules-tags' | 'insight-ops';

export type SectionKey =
  | 'skill-scenes'
  | 'configuration-center'
  | 'data-integration'
  | 'quick-search-content'
  | 'template-promotion-candidates'
  | 'account-permissions'
  | 'followup-rules'
  | 'customer-tags'
  | 'analytics-dashboard'
  | 'supervision-dashboard'
  | 'governance-settings'
  | 'version-management'
  | 'system-notices'
  | 'audit-logs'
  | 'system-health';

export type AdminSection = {
  key: SectionKey;
  groupKey: SectionGroupKey;
  group: string;
  module: string;
  title: string;
  subtitle: string;
  description: string;
  primaryAction: string;
};

export type AdminNavGroupDefinition = {
  key: SectionGroupKey;
  title: string;
  subtitle: string;
  defaultKey: SectionKey;
  pages: AdminSection[];
};

export type AdminNavGroup = AdminNavGroupDefinition;

export function buildAdminNavigation(
  sections: readonly AdminSection[],
  groups: readonly AdminNavGroupDefinition[],
  tagManagementOnly: boolean
): AdminNavGroup[] {
  const visibleGroups = tagManagementOnly
    ? groups.filter((group) => group.key === 'org-rules-tags')
    : groups;

  return visibleGroups.map((group) => {
    const pages = tagManagementOnly
      ? sections.filter((section) => section.key === 'customer-tags')
      : sections.filter((section) => section.groupKey === group.key);
    const defaultKey = tagManagementOnly ? 'customer-tags' : group.defaultKey;

    return { ...group, defaultKey, pages };
  });
}
