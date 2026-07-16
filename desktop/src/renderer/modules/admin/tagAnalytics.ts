export type TagMatchMode = 'ANY' | 'ALL';
export type TagGroupLogic = 'AND' | 'OR';

export type TagFilterGroup = {
  categoryId: number;
  valueIds: number[];
  match: TagMatchMode;
};

export type TagAnalyticsRequestInput = {
  keyword?: string;
  sourceChannels?: string[];
  leadTypes?: string[];
  assignedKeepers?: string[];
  intendedStores?: string[];
  intendedProjects?: string[];
  customerStages?: string[];
  updatedFrom?: string;
  updatedTo?: string;
  teamLeaderIds?: number[];
  tagFrom?: string;
  tagTo?: string;
  tagGroups?: TagFilterGroup[];
  tagGroupLogic?: TagGroupLogic;
};

export type TagAnalyticsRequest = {
  customerFilter: {
    keyword: string;
    sourceChannels: string[];
    leadTypes: string[];
    assignedKeepers: string[];
    intendedStores: string[];
    intendedProjects: string[];
    customerStages: string[];
    updatedFrom: string | null;
    updatedTo: string | null;
    tagGroups: TagFilterGroup[];
    tagGroupLogic: TagGroupLogic;
  };
  teamLeaderIds: number[];
  tagFrom: string | null;
  tagTo: string | null;
  granularity: 'DAY';
};

export type TagAnalyticsSummary = {
  matchedCustomerCount: number;
  taggedCustomerCount: number;
  activeAssignmentCount: number;
  coverageRate: number;
  systemAddedCount: number;
  manualAddedOrChangedCount: number;
  systemDecidedNoUpdateCount: number;
};

export type TagAnalyticsCategoryRow = {
  categoryId: number;
  categoryKey: string;
  categoryName: string;
  activeAssignmentCount: number;
  taggedCustomerCount: number;
};

export type TagAnalyticsTagRow = {
  categoryId: number;
  categoryKey: string;
  categoryName: string;
  valueId: number;
  valueCode: string;
  displayName: string;
  activeAssignmentCount: number;
  taggedCustomerCount: number;
};

export type TagAnalyticsDimensionRow = {
  key: string;
  label: string;
  activeAssignmentCount: number;
  taggedCustomerCount: number;
};

export type TagAnalyticsSourceRow = {
  sourceType: string;
  sourceLabel: string;
  addedAssignmentCount: number;
  affectedCustomerCount: number;
};

export type TagAnalyticsReasonRow = {
  reasonCode: string;
  reasonLabel: string;
  scope: 'CURRENT_GAP' | 'EVENT_WINDOW';
  customerCount: number;
  decisionCount: number;
  sampleReason: string | null;
};

export type TagAnalyticsTrendRow = {
  date: string;
  addedAssignmentCount: number;
  invalidatedAssignmentCount: number;
  netChange: number;
  systemAddedCount: number;
  manualAddedOrChangedCount: number;
};

export type TagAnalyticsValueOption = {
  value: string;
  label: string;
};

export type TagAnalyticsTeamOption = {
  leaderId: number;
  label: string;
};

export type TagAnalyticsEmployeeOption = {
  account: string;
  label: string;
  leaderId: number | null;
};

export type TagAnalyticsFilterOptions = {
  stores: TagAnalyticsValueOption[];
  teams: TagAnalyticsTeamOption[];
  employees: TagAnalyticsEmployeeOption[];
  customerSources: TagAnalyticsValueOption[];
  tagSources: TagAnalyticsValueOption[];
};

export type TagAnalyticsAppliedWindow = {
  tagFrom: string;
  tagTo: string;
  granularity: 'DAY';
};

export type TagAnalyticsResponse = {
  summary: TagAnalyticsSummary;
  categories: TagAnalyticsCategoryRow[];
  tags: TagAnalyticsTagRow[];
  stores: TagAnalyticsDimensionRow[];
  teams: TagAnalyticsDimensionRow[];
  employees: TagAnalyticsDimensionRow[];
  tagSources: TagAnalyticsSourceRow[];
  unupdatedReasons: TagAnalyticsReasonRow[];
  trend: TagAnalyticsTrendRow[];
  filterOptions: TagAnalyticsFilterOptions;
  appliedWindow: TagAnalyticsAppliedWindow;
};

function compact(values: string[] | undefined): string[] {
  return [...new Set((values ?? []).map((value) => value.trim()).filter(Boolean))];
}

function nullableText(value: string | undefined): string | null {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}

export function buildTagAnalyticsRequest(input: TagAnalyticsRequestInput): TagAnalyticsRequest {
  return {
    customerFilter: {
      keyword: input.keyword?.trim() || '',
      sourceChannels: compact(input.sourceChannels),
      leadTypes: compact(input.leadTypes),
      assignedKeepers: compact(input.assignedKeepers),
      intendedStores: compact(input.intendedStores),
      intendedProjects: compact(input.intendedProjects),
      customerStages: compact(input.customerStages),
      updatedFrom: nullableText(input.updatedFrom),
      updatedTo: nullableText(input.updatedTo),
      tagGroups: (input.tagGroups ?? []).map((group) => ({
        categoryId: Number(group.categoryId),
        valueIds: group.valueIds.map(Number),
        match: group.match
      })),
      tagGroupLogic: input.tagGroupLogic ?? 'AND'
    },
    teamLeaderIds: (input.teamLeaderIds ?? []).map(Number),
    tagFrom: nullableText(input.tagFrom),
    tagTo: nullableText(input.tagTo),
    granularity: 'DAY'
  };
}

function csvCell(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function csvSection(title: string, headers: string[], rows: unknown[][]): string {
  return [
    csvCell(title),
    headers.map(csvCell).join(','),
    ...rows.map((row) => row.map(csvCell).join(','))
  ].join('\n');
}

export function tagSourceLabel(value: string): string {
  return ({
    SYSTEM_INFERENCE: '系统推断',
    MANUAL: '人工设置',
    LEGACY_MIGRATION: '历史迁移'
  } as Record<string, string>)[value] ?? value;
}

export function reasonScopeLabel(value: string): string {
  return value === 'CURRENT_GAP'
    ? '当前覆盖缺口'
    : value === 'EVENT_WINDOW'
      ? '时间窗口事件'
      : value;
}

export function tagAnalyticsCsvSections(data: TagAnalyticsResponse): string[] {
  return [
    csvSection('标签统计概览', ['指标', '数值'], [
      ['匹配客户', data.summary.matchedCustomerCount],
      ['已打标签客户', data.summary.taggedCustomerCount],
      ['当前有效标签', data.summary.activeAssignmentCount],
      ['覆盖率', `${(data.summary.coverageRate * 100).toFixed(2)}%`],
      ['系统新增', data.summary.systemAddedCount],
      ['人工新增或修改', data.summary.manualAddedOrChangedCount],
      ['系统判断未更新', data.summary.systemDecidedNoUpdateCount]
    ]),
    csvSection('标签分类', ['分类名称', '分类代码', '有效标签数', '客户数'],
      data.categories.map((row) => [
        row.categoryName,
        row.categoryKey,
        row.activeAssignmentCount,
        row.taggedCustomerCount
      ])),
    csvSection('标签值', ['分类名称', '分类代码', '标签名称', '标签代码', '有效标签数', '客户数'],
      data.tags.map((row) => [
        row.categoryName,
        row.categoryKey,
        row.displayName,
        row.valueCode,
        row.activeAssignmentCount,
        row.taggedCustomerCount
      ])),
    csvSection('门店', ['门店', '有效标签数', '客户数'],
      data.stores.map((row) => [row.label, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('团队', ['团队', '团队标识', '有效标签数', '客户数'],
      data.teams.map((row) => [row.label, row.key, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('员工', ['员工', '账号', '有效标签数', '客户数'],
      data.employees.map((row) => [row.label, row.key, row.activeAssignmentCount, row.taggedCustomerCount])),
    csvSection('标签来源', ['来源', '来源代码', '新增标签数', '客户数'],
      data.tagSources.map((row) => [
        row.sourceLabel || tagSourceLabel(row.sourceType),
        row.sourceType,
        row.addedAssignmentCount,
        row.affectedCustomerCount
      ])),
    csvSection('未更新原因', ['原因', '原因代码', '范围', '客户数', '判断数', '示例原因'],
      data.unupdatedReasons.map((row) => [
        row.reasonLabel,
        row.reasonCode,
        reasonScopeLabel(row.scope),
        row.customerCount,
        row.decisionCount,
        row.sampleReason ?? ''
      ])),
    csvSection('标签趋势', ['日期', '新增', '失效', '净变化', '系统新增', '人工新增或修改'],
      data.trend.map((row) => [
        row.date,
        row.addedAssignmentCount,
        row.invalidatedAssignmentCount,
        row.netChange,
        row.systemAddedCount,
        row.manualAddedOrChangedCount
      ]))
  ];
}
