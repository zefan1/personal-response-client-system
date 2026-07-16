import { describe, expect, it } from 'vitest';
import { buildTagAnalyticsRequest, tagAnalyticsCsvSections } from './tagAnalytics';

describe('tag analytics helpers', () => {
  it('serializes customer, team, event-window and tag filters', () => {
    const payload = buildTagAnalyticsRequest({
      sourceChannels: ['企微'],
      leadTypes: ['XIAN_SUO'],
      assignedKeepers: ['keeper-1'],
      intendedStores: ['万江店'],
      updatedFrom: '2026-07-01T00:00:00',
      updatedTo: '2026-07-16T23:59:59',
      teamLeaderIds: [9],
      tagFrom: '2026-07-10T00:00:00',
      tagTo: '2026-07-16T23:59:59',
      tagGroups: [{ categoryId: 10, valueIds: [101], match: 'ANY' }],
      tagGroupLogic: 'AND'
    });

    expect(payload).toMatchObject({
      customerFilter: {
        sourceChannels: ['企微'],
        assignedKeepers: ['keeper-1'],
        intendedStores: ['万江店'],
        tagGroups: [{ categoryId: 10, valueIds: [101], match: 'ANY' }]
      },
      teamLeaderIds: [9],
      granularity: 'DAY'
    });
  });

  it('exports display names and internal codes', () => {
    const sections = tagAnalyticsCsvSections({
      summary: {
        matchedCustomerCount: 2,
        taggedCustomerCount: 1,
        activeAssignmentCount: 1,
        coverageRate: 0.5,
        systemAddedCount: 1,
        manualAddedOrChangedCount: 0,
        systemDecidedNoUpdateCount: 1
      },
      categories: [],
      tags: [{
        categoryId: 10,
        categoryKey: 'intent_level',
        categoryName: '意向等级',
        valueId: 101,
        valueCode: 'HIGH',
        displayName: '高意向',
        activeAssignmentCount: 1,
        taggedCustomerCount: 1
      }],
      stores: [],
      teams: [],
      employees: [],
      tagSources: [],
      unupdatedReasons: [],
      trend: [],
      filterOptions: { stores: [], teams: [], employees: [], customerSources: [], tagSources: [] },
      appliedWindow: {
        tagFrom: '2026-07-10T00:00:00',
        tagTo: '2026-07-16T23:59:59',
        granularity: 'DAY'
      }
    });

    expect(sections.join('\n')).toContain('高意向');
    expect(sections.join('\n')).toContain('HIGH');
  });
});
