import { describe, expect, it } from 'vitest';
import { buildAdminNavigation, type AdminNavGroupDefinition, type AdminSection } from './adminNavigation';

const sections: AdminSection[] = [
  section('skill-scenes', 'config-center'),
  section('configuration-center', 'config-center'),
  section('data-integration', 'data-content'),
  section('quick-search-content', 'data-content'),
  section('account-permissions', 'org-rules-tags'),
  section('followup-rules', 'org-rules-tags'),
  section('customer-tags', 'org-rules-tags'),
  section('analytics-dashboard', 'insight-ops'),
  section('version-management', 'insight-ops'),
  section('system-notices', 'insight-ops'),
  section('audit-logs', 'insight-ops'),
  section('system-health', 'insight-ops')
];

const navigationGroups: AdminNavGroupDefinition[] = [
  group('config-center', 'skill-scenes'),
  group('data-content', 'data-integration'),
  group('org-rules-tags', 'account-permissions'),
  group('insight-ops', 'analytics-dashboard')
];

describe('buildAdminNavigation', () => {
  it('keeps every management section in its existing navigation group and order', () => {
    expect(buildAdminNavigation(sections, navigationGroups, false).map((group) => ({
      key: group.key,
      defaultKey: group.defaultKey,
      pages: group.pages.map((page) => page.key)
    }))).toEqual([
      { key: 'config-center', defaultKey: 'skill-scenes', pages: ['skill-scenes', 'configuration-center'] },
      { key: 'data-content', defaultKey: 'data-integration', pages: ['data-integration', 'quick-search-content'] },
      { key: 'org-rules-tags', defaultKey: 'account-permissions', pages: ['account-permissions', 'followup-rules', 'customer-tags'] },
      { key: 'insight-ops', defaultKey: 'analytics-dashboard', pages: ['analytics-dashboard', 'version-management', 'system-notices', 'audit-logs', 'system-health'] }
    ]);
  });

  it('limits tag-management-only accounts to the customer-tag section', () => {
    const groups = buildAdminNavigation(sections, navigationGroups, true);

    expect(groups).toHaveLength(1);
    expect(groups[0].key).toBe('org-rules-tags');
    expect(groups[0].defaultKey).toBe('customer-tags');
    expect(groups[0].pages.map((page) => page.key)).toEqual(['customer-tags']);
  });
});

function group(key: AdminNavGroupDefinition['key'], defaultKey: AdminSection['key']): AdminNavGroupDefinition {
  return { key, title: key, subtitle: key, defaultKey, pages: [] };
}

function section(key: AdminSection['key'], groupKey: AdminSection['groupKey']): AdminSection {
  return { key, groupKey, group: groupKey, module: key, title: key, subtitle: key, description: key, primaryAction: key };
}
