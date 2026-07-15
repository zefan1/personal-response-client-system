import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminConsole from './AdminConsole.vue';

const apiMocks = vi.hoisted(() => ({
  getBlob: vi.fn(),
  getJson: vi.fn(),
  postForm: vi.fn(),
  postJson: vi.fn(),
  putJson: vi.fn(),
  deleteJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getBlob: apiMocks.getBlob,
  getJson: apiMocks.getJson,
  postForm: apiMocks.postForm,
  postJson: apiMocks.postJson,
  putJson: apiMocks.putJson,
  deleteJson: apiMocks.deleteJson
}));

type MountedConsole = {
  app: App<Element>;
  host: HTMLDivElement;
};

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
      removeItem: vi.fn((key: string) => store.delete(key)),
      clear: vi.fn(() => store.clear())
    },
    configurable: true
  });
}

const apiData: Record<string, unknown> = {
  '/admin/api/v1/skills': {
    items: [
      { id: 1, scene: 'OPENING', leadType: 'TUAN_GOU', skillId: 'skill_opening', skillName: '开场白助手', priority: 90, enabled: true },
      { id: 2, scene: 'CHAT_RECOGNIZE', leadType: 'PENDING', skillId: 'skill_recognize', skillName: '聊天识别助手', priority: 80, enabled: true }
    ]
  },
  '/admin/api/v1/skills/available': {
    items: [{ skillId: 'skill_opening', skillName: '开场白助手' }, { skillId: 'skill_reply', skillName: '主动回复助手' }]
  },
  '/admin/api/v1/analytics/skill-calls': { summary: { calls: 8, successRate: '99%' } },
  '/admin/api/v1/skill-environments': {
    items: [
      { id: 1, envName: '生产环境', baseUrl: 'https://skill.example.com', apiKeyLast4: '1234', active: true },
      { id: 3, envName: '备用环境', baseUrl: 'https://skill-backup.example.com', apiKeyLast4: '5678', active: false }
    ]
  },
  '/admin/api/v1/image-environments': {
    items: [{ id: 2, envName: '识图生产', baseUrl: 'https://image.example.com', lastTestOk: true, lastTestAt: '2026-07-03T08:00:00Z', isActive: true }]
  },
  '/admin/api/v1/llm-environments': {
    items: [
      { id: 4, envName: 'LLM 主模型', baseUrl: 'https://llm.example.com', apiKeyLast4: '9999', model: 'gpt-4.1-mini', protocol: 'OPENAI_COMPATIBLE', timeoutMs: 10000, temperature: 0.2, maxTokens: 1024, lastTestOk: true, lastTestAt: '2026-07-03T08:30:00Z', active: true },
      { id: 5, envName: 'LLM 备用', baseUrl: 'https://llm-backup.example.com', apiKeyLast4: '8888', model: 'qwen-plus', protocol: 'OPENAI_COMPATIBLE', timeoutMs: 15000, temperature: 0.3, maxTokens: 2048, active: false }
    ]
  },
  '/admin/api/v1/llm-routes': {
    items: [
      { id: 6, scene: 'REPLY_GENERATION', leadType: 'PENDING', environmentId: 4, environmentName: 'LLM 主模型', model: 'gpt-4.1-mini', protocol: 'OPENAI_COMPATIBLE', priority: 10, enabled: true }
    ]
  },
  '/admin/api/v1/llm-routes/scenes': ['REPLY_GENERATION', 'PROFILE_EXTRACTION', 'FOLLOWUP_SUGGESTION', 'ABNORMAL_DETECTION', 'SUMMARY'],
  '/admin/api/v1/analytics/llm-calls': {
    summary: { totalCalls: 3, successRate: 0.6667, avgResponseTime: 320 },
    details: [{ scene: 'REPLY_GENERATION', leadType: 'PENDING', environmentId: 4, model: 'gpt-4.1-mini', totalCalls: 3, successCount: 2, failCount: 1, avgResponseTime: 320 }]
  },
  '/admin/api/v1/configs': {
    'skill.system_prompt_format': '按客户阶段输出',
    'skill.system_prompt_red_lines': '["不得承诺疗效"]',
    'match.tag_removal_rules': '["L1-"]',
    'skill.fallback_reply': '稍后回复',
    'image.recognition_prompt': '识别昵称、手机号和聊天内容',
    'skill.regenerate_max_count': '3',
    'skill.timeout_ms': '10000',
    'skill.circuit_breaker_window_s': '30',
    'skill.circuit_breaker_failure_rate': '0.5',
    'skill.circuit_breaker_min_calls': '5',
    'skill.circuit_breaker_open_s': '30',
    'skill.alert_failure_rate': '0.3',
    'skill.alert_failure_duration_minutes': '15',
    'profile.extract_timeout_ms': '8000',
    'image.model': 'qwen3-vl-plus',
    'image.timeout_ms': '5000',
    'image.max_size_bytes': '5242880',
    'image.max_dimension_px': '1920',
    'image.compress_quality': '85',
    'image.consecutive_failures_alert': '3',
    'llm.api_base_url': 'https://llm.example.com',
    'llm.api_key': '****9999',
    'llm.model': 'gpt-4.1-mini',
    'llm.protocol': 'OPENAI_COMPATIBLE',
    'llm.timeout_ms': '10000',
    'llm.temperature': '0.2',
    'llm.max_tokens': '1024',
    'llm.reply_generation.enabled': 'false',
    'llm.reply_generation.fallback_to_skill': 'true',
    'llm.reply_generation.temperature': '',
    'llm.reply_generation.max_tokens': '900',
    'llm.reply_generation.system_prompt': '生成三条可直接发送的回复建议',
    'llm.profile_extraction.enabled': 'false',
    'llm.profile_extraction.fallback_to_skill': 'true',
    'llm.profile_extraction.temperature': '',
    'llm.profile_extraction.max_tokens': '700',
    'llm.profile_extraction.system_prompt': '提取客户档案更新建议',
    'llm.followup_suggestion.enabled': 'false',
    'llm.followup_suggestion.temperature': '',
    'llm.followup_suggestion.max_tokens': '500',
    'llm.followup_suggestion.system_prompt': '生成下次跟进建议',
    'llm.abnormal_detection.enabled': 'false',
    'llm.abnormal_detection.temperature': '',
    'llm.abnormal_detection.max_tokens': '500',
    'llm.abnormal_detection.system_prompt': '识别客户不满和流失风险',
    'llm.summary.enabled': 'false',
    'llm.summary.temperature': '',
    'llm.summary.max_tokens': '500',
    'llm.summary.system_prompt': '生成会话摘要',
    'desktop.clipboard_screenshot_confirm_prompt_s': '10',
    'table.api_base_url': 'https://table.example.com',
    'table.api_key': '****4321',
    'table.write_timeout_ms': '10000',
    'table.retry_max_count': '5',
    'table.retry_interval_s': '60',
    'table.alert_failure_hours': '1',
    'table.alert_notify_target': 'ADMIN',
    'table.queue_warn_threshold': '100',
    'table.queue_alert_threshold': '1000',
    'cache.sync_cron': '0 */30 * * * *',
    'cache.ttl_seconds': '900',
    'cache.sync_timeout_ms': '10000',
    'datasource.mapping_version_max': '50',
    'datasource.import_max_rows': '5000',
    'datasource.manual_sync_timeout_s': '60',
    'datasource.sync_status_refresh_s': '30'
  },
  '/admin/api/v1/datasources': {
    items: [{ id: 10, name: '企微客资表', sheetId: 'sheet-a', sourceTable: 'leads', enabled: true }]
  },
  '/admin/api/v1/customer-fields': {
    items: [
      { key: 'phone', label: '手机号' },
      { key: 'nickname', label: '客户昵称' },
      { key: 'intentLevel', label: '意向等级' },
      { key: 'customerStage', label: '客户阶段' }
    ]
  },
  '/admin/api/v1/datasources/sync-status': {
    items: [{ datasourceId: 10, syncStatus: 'FAILED', mappingCount: 2, failures: ['手机号列为空'] }]
  },
  '/admin/api/v1/datasources/import-logs': {
    logs: [{ id: 1, fileName: 'last.csv', totalRows: 3, created: 1, updated: 1, skipped: 1, errorDetail: '[RowError[row=3, reason=phone invalid]]', createdAt: '2026-07-03T09:00:00Z' }],
    total: 1,
    limit: 50
  },
  '/admin/api/v1/customers/search': {
    items: [
      { id: 11, phone: '13800001111', nickname: '王女士', sourceChannel: '企微', leadType: 'TUAN_GOU', assignedKeeper: '18800000001', intendedStore: '万江店', intendedProject: '产后修复', customerStage: 'PENDING', intentLevel: 'HIGH', lastFollowupAt: '2026-07-03T09:00:00Z', sourceTable: '私域客资管理表', updatedAt: '2026-07-03T10:00:00Z' }
    ],
    total: 1,
    page: 1,
    size: 20,
    totalPages: 1
  },
  '/admin/api/v1/datasources/10/mappings': {
    mappings: [
      { id: 1, sourceField: 'phone', targetField: 'phone', enabled: true },
      { id: 2, sourceField: 'nickname', targetField: 'nickname', enabled: false }
    ],
    currentVersion: 3
  },
  '/admin/api/v1/datasources/10/columns': {
    datasourceId: 10,
    sourceTable: 'leads',
    columns: [
      { name: 'phone', mapped: true, targetField: 'phone', enabled: true },
      { name: 'nickname', mapped: true, targetField: 'nickname', enabled: false },
      { name: 'store', mapped: false }
    ],
    source: 'MAPPING_CONFIG',
    fetchStatus: 'UNAVAILABLE',
    externalFetchAvailable: false,
    fallback: true,
    fetchError: 'sheet timeout'
  },
  '/admin/api/v1/datasources/10/mappings/compare': {
    summary: { currentCount: 2, baselineCount: 2, added: 1, removed: 1, changed: 1, unchanged: 0 },
    diff: {
      added: [{ sourceField: 'nickname', targetField: 'nickname', enabled: false }],
      removed: [{ sourceField: 'old_phone', targetField: 'phone', enabled: true }],
      changed: [{ sourceField: 'phone', before: { sourceField: 'phone', targetField: 'mobile', enabled: true }, after: { sourceField: 'phone', targetField: 'phone', enabled: true } }],
      unchanged: []
    }
  },
  '/admin/api/v1/quick-search/items': {
    items: [{ id: 20, contentType: 'TEMPLATE', leadType: 'GENERAL', title: '开场话术', shortcutCode: 'hi', content: '您好', enabled: true }],
    total: 21,
    page: 1,
    size: 20,
    totalPages: 2
  },
  '/admin/api/v1/accounts': {
    list: [
      { id: 30, displayName: '管理员', phone: '18800000000', role: 'ADMIN', isEnabled: true, permissions: ['TAG_MANAGEMENT'], lastLoginAt: '2026-07-03T09:00:00Z' },
      { id: 31, displayName: '组长', phone: '18800000001', role: 'LEADER', isEnabled: true, permissions: [], lastLoginAt: '2026-07-03T09:05:00Z' }
    ],
    total: 42,
    page: 1,
    pageSize: 20,
    totalPages: 3
  },
  '/admin/api/v1/rules': {
    items: [
      { id: 40, name: '24 小时未跟进提醒', actionType: 'ALERT', priority: 90, enabled: true, builtin: true, conditionPreview: '待确认客户超过 24 小时未跟进' },
      { id: 41, name: '高意向标签建议', actionType: 'TAG_CHANGE', priority: 70, enabled: false, builtin: false, conditionPreview: '高意向客户 12 小时未跟进' }
    ],
    total: 22,
    page: 1,
    size: 20,
    totalPages: 2
  },
  '/admin/api/v1/tags/categories': {
    items: [{
      id: 50,
      categoryKey: 'intent_level',
      categoryName: '意向等级',
      purpose: '用于判断客户购买意向',
      boundField: 'intentLevel',
      selectionMode: 'SINGLE',
      systemInferenceEnabled: true,
      manualEditEnabled: true,
      autoUpdateMode: 'REPLACE',
      minConfidence: 0.85,
      minEvidenceMessages: 2,
      cooldownHours: 12,
      uncertainPolicy: 'KEEP_CURRENT',
      useForReply: true,
      useForFilter: true,
      useForStatistics: true,
      useForFollowupRules: true,
      isBuiltin: true,
      isEnabled: true,
      sortOrder: 10,
      mergedIntoId: null,
      version: 4,
      values: [],
      impact: { customerCount: 12, ruleCount: 2, historyCount: 8 },
      updatedAt: '2026-07-03T09:00:00Z'
    }],
    total: 21,
    page: 1,
    size: 20,
    totalPages: 2
  },
  '/admin/api/v1/tags/categories/50': {
    id: 50,
    categoryKey: 'intent_level',
    categoryName: '意向等级',
    purpose: '用于判断客户购买意向',
    boundField: 'intentLevel',
    selectionMode: 'SINGLE',
    systemInferenceEnabled: true,
    manualEditEnabled: true,
    autoUpdateMode: 'REPLACE',
    minConfidence: 0.85,
    minEvidenceMessages: 2,
    cooldownHours: 12,
    uncertainPolicy: 'KEEP_CURRENT',
    useForReply: true,
    useForFilter: true,
    useForStatistics: true,
    useForFollowupRules: true,
    isBuiltin: true,
    isEnabled: true,
    sortOrder: 10,
    mergedIntoId: null,
    version: 4,
    values: [],
    impact: { customerCount: 12, ruleCount: 2, historyCount: 8 },
    updatedAt: '2026-07-03T09:00:00Z'
  },
  '/admin/api/v1/tags/values': {
    items: [{
      id: 51,
      categoryId: 50,
      categoryKey: 'intent_level',
      tagValue: 'high_intent',
      displayName: '高意向',
      meaning: '近期有明确购买计划',
      applicableWhen: '主动询价并确认到店时间',
      notApplicableWhen: '仅咨询基础信息',
      positiveExamples: '本周可以到店体验吗',
      negativeExamples: '先了解一下',
      synonyms: ['想尽快购买', '近期到店'],
      systemSelectable: true,
      manualSelectable: true,
      isEnabled: true,
      sortOrder: 10,
      mergedIntoId: null,
      version: 7,
      impact: { customerCount: 9, ruleCount: 1, historyCount: 5 },
      updatedAt: '2026-07-03T09:10:00Z'
    }, {
      id: 52,
      categoryId: 50,
      categoryKey: 'intent_level',
      tagValue: 'medium_intent',
      displayName: '中意向',
      meaning: '有兴趣但购买时间不明确',
      synonyms: [],
      systemSelectable: true,
      manualSelectable: true,
      isEnabled: true,
      sortOrder: 20,
      mergedIntoId: null,
      version: 3,
      impact: { customerCount: 3, ruleCount: 0, historyCount: 2 },
      updatedAt: '2026-07-03T09:20:00Z'
    }],
    total: 22,
    page: 1,
    size: 20,
    totalPages: 2
  },
  '/admin/api/v1/tags/values/51': {
    id: 51,
    categoryId: 50,
    categoryKey: 'intent_level',
    tagValue: 'high_intent',
    displayName: '高意向',
    meaning: '近期有明确购买计划',
    applicableWhen: '主动询价并确认到店时间',
    notApplicableWhen: '仅咨询基础信息',
    positiveExamples: '本周可以到店体验吗',
    negativeExamples: '先了解一下',
    synonyms: ['想尽快购买', '近期到店'],
    systemSelectable: true,
    manualSelectable: true,
    isEnabled: true,
    sortOrder: 10,
    mergedIntoId: null,
    version: 7,
    impact: { customerCount: 9, ruleCount: 1, historyCount: 5 },
    updatedAt: '2026-07-03T09:10:00Z'
  },
  '/admin/api/v1/analytics/overview': {
    summary: { totalCalls: 18, adoptionRate: '98%', avgResponseTimeMs: 1200, activeCallerCount: 2 },
    dailyTrend: [{ date: '2026-07-03', totalCalls: 18, adoptionCount: 16, adoptionRate: '88%', avgResponseTimeMs: 1200 }]
  },
  '/admin/api/v1/analytics/funnels': { tuanGou: { stages: [{ stage: '已分配', stageKey: 'ASSIGNED', count: 12, layerRate: '100%', totalRate: '100%' }] } },
  '/admin/api/v1/analytics/staff': { list: [{ caller: '18800000001', totalCustomers: 6, totalCalls: 8, adoptionCount: 7, adoptionRate: '87%', overdueCount: 1, silentCount: 2 }] },
  '/admin/api/v1/analytics/sources': { list: [{ sourceChannel: '企微', total: 12, tuanGouCount: 7, xianSuoCount: 5, arrivedCount: 3, arrivalRate: '25%' }] },
  '/admin/api/v1/analytics/stages': { list: [{ customerStage: 'PENDING', total: 5, tuanGouCount: 2, xianSuoCount: 3 }] },
  '/admin/api/v1/analytics/health': { summary: { totalCustomers: 12, keeperCount: 2, overdueCount: 1, silentCount: 2 }, systemAlerts: [] },
  '/admin/api/v1/analytics/lifecycle': { list: [{ leadType: 'TUAN_GOU', allocationToFirstContact: 1.5, allocationToArrival: 4.2, estimateSource: 'customers.updated_at' }] },
  '/admin/api/v1/analytics/risks': { customers: [{ phone: '18800001111', nickname: '张三', leadType: 'TUAN_GOU', customerStage: 'PENDING', assignedKeeper: '18800000001', lastFollowupAt: '2026-07-03T09:00:00Z' }], alerts: [] },
  '/admin/api/v1/analytics/content-ranking': { list: [{ action: 'COPY_REPLY', targetType: 'template', targetId: 'hi', useCount: 9, sampleDetail: '开场话术' }], leadTypeFilterApplied: null },
  '/admin/api/v1/versions': {
    items: [
      { id: 60, version: '1.0.1', platform: 'WINDOWS', updateStrategy: 'OPTIONAL', status: 'DRAFT' },
      { id: 61, version: '1.0.0', platform: 'WINDOWS', updateStrategy: 'FORCED', status: 'PUBLISHED', publishedAt: '2026-07-03T08:00:00Z' },
      { id: 62, version: '0.9.9', platform: 'WINDOWS', updateStrategy: 'OPTIONAL', status: 'REVOKED', revokedAt: '2026-07-03T08:30:00Z', revokeReason: '安装包异常', alternativeVersion: '1.0.0' }
    ],
    total: 3,
    page: 1,
    size: 20,
    totalPages: 2
  },
  '/admin/api/v1/notices': {
    items: [
      { id: 70, title: '系统维护', level: 'INFO', source: 'MANUAL', status: 'SCHEDULED', isStopped: false, content: '今晚维护', publishAt: '2026-07-04T12:00:00Z', expireAt: '2026-07-05T12:00:00Z' },
      { id: 71, title: '接口异常', level: 'WARN', source: 'AUTO', status: 'PUBLISHED', isStopped: false, content: '识图短暂异常', publishAt: '2026-07-04T10:00:00Z', expireAt: '2026-07-04T11:00:00Z' },
      { id: 72, title: '旧公告', level: 'INFO', source: 'MANUAL', status: 'PUBLISHED', isStopped: true, content: '已停止' }
    ],
    total: 3,
    page: 1,
    size: 20,
    totalPages: 2
  },
  '/admin/api/v1/audit-logs': {
    items: [{ id: 80, action: 'CREATE_NOTICE', actionLabel: '创建公告', actionGroup: '公告操作', targetType: 'notice', targetTypeLabel: '公告', targetId: 'notice-1', operator: 'admin', detailSummary: '创建公告：系统维护', detailParsed: { title: '系统维护' }, detail: '{"title":"系统维护"}', createdAt: '2026-07-03T09:00:00Z' }],
    total: 1,
    page: 1,
    size: 20,
    totalPages: 1,
    retentionDays: 90,
    earliestCreatedAt: '2026-07-01T00:00:00Z'
  },
  '/admin/api/v1/audit-logs/actions': {
    actions: [
      { action: 'CREATE_NOTICE', label: '创建公告', group: '公告操作' },
      { action: 'UPDATE_NOTICE', label: '编辑公告', group: '公告操作' }
    ],
    targetTypes: [{ type: 'notice', label: '公告' }]
  },
  '/admin/api/v1/health': {
    status: 'OK',
    refreshIntervalS: 45,
    runtimeMode: { mockExternals: true, label: '本地模拟模式', description: '外部表格、AI 技能和图片识别使用本地 Mock 响应。' },
    components: {
      db: { status: 'UP', duration: 'PT1M' },
      redis: { status: 'UP', duration: 'PT1M' },
      skill: { status: 'UP', duration: 'PT1M' },
      imageRecognition: { status: 'UP', duration: 'PT1M' },
      wecomTable: { status: 'UP', duration: 'PT1M' }
    },
    recentAlerts: [{ id: 90, alertType: 'IMAGE_DOWN', level: 'WARN', status: 'OPEN', message: '识图异常', occurredAt: '2026-07-03T09:00:00Z', detail: '{"lastError":"timeout"}' }]
  }
};

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function flushSave() {
  await flushUi();
  await flushUi();
}

async function mountConsole(props: { accountName?: string; tagManagementOnly?: boolean } = {}): Promise<MountedConsole> {
  localStorage.setItem('desktop_config', JSON.stringify({ apiBaseUrl: 'http://localhost:8080', accessToken: 'token-a' }));
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AdminConsole, { accountName: props.accountName ?? 'admin', tagManagementOnly: props.tagManagementOnly ?? false });
  app.mount(host);
  await flushUi();
  return { app, host };
}

function findButton(host: HTMLElement, text: string): HTMLButtonElement {
  const button = [...host.querySelectorAll('button')].find((item) => item.textContent?.includes(text)) as HTMLButtonElement | undefined;
  expect(button).toBeTruthy();
  return button as HTMLButtonElement;
}

function findSubnavButton(host: HTMLElement, text: string): HTMLButtonElement {
  const button = [...host.querySelectorAll('.ops-admin-subnav-button')].find((item) => item.textContent?.includes(text)) as HTMLButtonElement | undefined;
  expect(button).toBeTruthy();
  return button as HTMLButtonElement;
}

function mainText(host: HTMLElement): string {
  return host.querySelector('.ops-admin-main')?.textContent ?? '';
}

function setInputValue(element: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement, value: string) {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

function controlByLabel<T extends HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(host: HTMLElement, text: string): T {
  const label = [...host.querySelectorAll('label')].find((item) => item.querySelector('.ops-label-title')?.textContent?.includes(text));
  const control = label?.querySelector('input, select, textarea') as T | null;
  expect(control).toBeTruthy();
  return control as T;
}

describe('AdminConsole product surface', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    localStorage.clear();
    apiMocks.getBlob.mockResolvedValue({ blob: new Blob(['csv']), filename: 'tags.csv' });
    apiMocks.getJson.mockImplementation(async (path: string) => ({ success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null }));
    apiMocks.postForm.mockResolvedValue({ success: true, data: { totalRows: 1, created: 1, updated: 0, skipped: 0, errors: [] }, errorCode: null, message: null });
    apiMocks.postJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.putJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.deleteJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    apiMocks.getJson.mockReset();
    apiMocks.getBlob.mockReset();
    apiMocks.postForm.mockReset();
    apiMocks.postJson.mockReset();
    apiMocks.putJson.mockReset();
    apiMocks.deleteJson.mockReset();
    vi.restoreAllMocks();
  });

  it('renders grouped module navigation and opens configuration center by default', async () => {
    const { app, host } = await mountConsole();

    expect(host.querySelector('.ops-admin-shell')).toBeTruthy();
    expect([...host.querySelectorAll('.ops-admin-group-button > span')].map((item) => item.textContent)).toEqual([
      '配置中心',
      '数据源与内容',
      '组织与规则',
      '分析与系统'
    ]);
    expect([...host.querySelectorAll('.ops-admin-subnav-button small')].map((item) => item.textContent)).toEqual([
      'Skill 场景管理',
      '配置中心',
      '客户数据对接',
      '速搜内容管理',
      '账号与权限',
      '跟进规则引擎配置',
      '客户标签与分层',
      '运营分析看板',
      '版本管理',
      '系统公告',
      '操作审计日志',
      '系统健康监控'
    ]);
    expect(host.textContent).toContain('Skill 场景绑定');
    expect(host.textContent).toContain('开场白助手');
    expect(host.textContent).toContain('聊天识别');
    expect(host.textContent).not.toContain('Prompt 与规则');
    findSubnavButton(host, '配置中心').click();
    await flushSave();
    expect(mainText(host)).toContain('Prompt 与规则');
    expect(host.textContent).toContain('本地模拟模式');
    expect(host.querySelector('.admin-read-panel')).toBeFalsy();
    expect(host.querySelector('.admin-action-panel')).toBeFalsy();
    expect(host.textContent).not.toContain('请求体 JSON');
    expect(host.textContent).not.toContain('目标 ID');
    expect(host.textContent).not.toContain('GET /admin');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/skills');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/skills/available');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/configs');

    app.unmount();
  });

  it('opens a business drawer and saves with structured form fields', async () => {
    const { app, host } = await mountConsole();

    findButton(host, '新增绑定').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement | null;
    expect(drawer).toBeTruthy();
    expect(drawer?.textContent).toContain('Skill 场景绑定');
    expect(drawer?.textContent).not.toContain('请求体 JSON');

    const textInputs = [...drawer!.querySelectorAll('input[type="text"]')] as HTMLInputElement[];
    const priorityInput = drawer!.querySelector('input[type="number"]') as HTMLInputElement;
    const selects = [...drawer!.querySelectorAll('select')] as HTMLSelectElement[];
    expect([...selects[0].options].map((option) => option.value)).toEqual([
      'CHAT_RECOGNIZE',
      'OPENING',
      'ACTIVE_REPLY',
      'REGENERATE',
      'PROFILE_EXTRACT'
    ]);
    setInputValue(selects[0], 'PROFILE_EXTRACT');
    setInputValue(selects[1], 'XIAN_SUO');
    setInputValue(selects[2], 'skill_reply');
    setInputValue(textInputs[0], '主动回复助手');
    setInputValue(priorityInput, '77');

    findButton(drawer!, '保存').click();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/skills', {
      scene: 'PROFILE_EXTRACT',
      leadType: 'XIAN_SUO',
      skillId: 'skill_reply',
      skillName: '主动回复助手',
      priority: 77
    });

    app.unmount();
  });

  it('renders structured profile analysis details from the Skill online test', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      const basePath = path.split('?')[0];
      const data = basePath === '/admin/api/v1/skills'
        ? {
            items: [{
              id: 3,
              scene: 'PROFILE_EXTRACT',
              leadType: 'PENDING',
              skillId: 'skill_profile',
              skillName: '档案提取助手',
              priority: 70,
              enabled: true
            }]
          }
        : apiData[path] ?? apiData[basePath] ?? { items: [] };
      return { success: true, data, errorCode: null, message: null };
    });
    apiMocks.postJson.mockImplementation(async (path: string) => {
      if (path === '/admin/api/v1/skills/3/test') {
        return {
          success: true,
          data: {
            responseTimeMs: 88,
            suggestions: [],
            rawResponse: null,
            profileAnalysis: {
              profileUpdates: {
                fields: {
                  nickname: { value: 'Alice', confidence: 'HIGH' }
                }
              },
              tagDecisions: [{
                categoryCode: 'custom_goal',
                tagCodes: ['GOAL_B'],
                confidence: 0.95,
                evidence: '客户明确表达目标',
                resultType: 'UPDATE',
                requestedAction: 'ADD'
              }]
            }
          },
          errorCode: null,
          message: null
        };
      }
      return { success: true, data: {}, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();
    const textarea = host.querySelector('textarea') as HTMLTextAreaElement;
    setInputValue(textarea, '客户明确表达目标');
    const row = [...host.querySelectorAll('.ops-table-row')]
      .find((item) => item.textContent?.includes('档案提取助手')) as HTMLElement;

    findButton(row, '测试').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/skills/3/test', { testMessage: '客户明确表达目标' });
    expect(host.textContent).toContain('档案字段 nickname：Alice（HIGH）');
    expect(host.textContent).toContain('custom_goal：更新 · 新增 · GOAL_B · 95%');
    expect(host.textContent).toContain('依据：客户明确表达目标');

    app.unmount();
  });

  it('saves configuration center prompt, external gateway, and runtime config keys', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();
    expect(mainText(host)).toContain('LLM 思考环境');
    expect(mainText(host)).toContain('LLM 主模型');
    expect(mainText(host)).toContain('gpt-4.1-mini');
    expect(mainText(host)).toContain('LLM 回复生成');
    expect(mainText(host)).toContain('LLM 档案提取');
    expect(mainText(host)).toContain('LLM 跟进建议');
    expect(mainText(host)).toContain('LLM 异常识别');
    expect(mainText(host)).toContain('LLM 总结补位');
    expect(mainText(host)).toContain('LLM 场景路由');
    expect(mainText(host)).toContain('回复生成');
    expect(mainText(host)).toContain('LLM 调用统计');
    expect(mainText(host)).toContain('66.7%');
    expect(mainText(host)).toContain('识图提示词');
    expect(mainText(host)).toContain('换一组次数上限');
    expect(mainText(host)).toContain('企微表格网关');
    expect(mainText(host)).toContain('数据同步策略');

    findButton(host, '保存 Skill 参数').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.timeout_ms', { value: '10000' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.circuit_breaker_failure_rate', { value: '0.5' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/profile.extract_timeout_ms', { value: '8000' });

    findButton(host, '保存识图参数').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.model', { value: 'qwen3-vl-plus' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.max_size_bytes', { value: '5242880' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.compress_quality', { value: '85' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/desktop.clipboard_screenshot_confirm_prompt_s', { value: '10' });

    findButton(host, '保存回复生成').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.fallback_to_skill', { value: 'true' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.max_tokens', { value: '900' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.system_prompt', { value: '生成三条可直接发送的回复建议' });

    findButton(host, '保存档案提取').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.fallback_to_skill', { value: 'true' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.max_tokens', { value: '700' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.system_prompt', { value: '提取客户档案更新建议' });

    findButton(host, '保存跟进建议').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.followup_suggestion.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.followup_suggestion.max_tokens', { value: '500' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.followup_suggestion.system_prompt', { value: '生成下次跟进建议' });

    findButton(host, '保存异常识别').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.abnormal_detection.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.abnormal_detection.max_tokens', { value: '500' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.abnormal_detection.system_prompt', { value: '识别客户不满和流失风险' });

    findButton(host, '保存总结补位').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.summary.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.summary.max_tokens', { value: '500' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.summary.system_prompt', { value: '生成会话摘要' });

    findButton(host, '保存表格参数').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/table.api_base_url', { value: 'https://table.example.com' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/table.retry_max_count', { value: '5' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/table.queue_alert_threshold', { value: '1000' });
    expect(apiMocks.putJson).not.toHaveBeenCalledWith('/admin/api/v1/configs/table.api_key', expect.anything());
    const beforeTableKeySaveCalls = apiMocks.putJson.mock.calls.length;

    const tableKeyInput = host.querySelector('input[aria-label="企微表格网关 API Key"]') as HTMLInputElement;
    setInputValue(tableKeyInput, 'new-table-secret');
    await flushUi();
    findButton(host, '保存表格参数').click();
    await flushSave();
    expect(apiMocks.putJson.mock.calls.slice(beforeTableKeySaveCalls)).toContainEqual(['/admin/api/v1/configs/table.api_key', { value: 'new-table-secret' }]);

    findButton(host, '保存同步策略').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/cache.sync_cron', { value: '0 */30 * * * *' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/cache.ttl_seconds', { value: '900' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/datasource.manual_sync_timeout_s', { value: '60' });

    findButton(host, '保存配置').click();
    await flushSave();

    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.system_prompt_format', { value: '按客户阶段输出' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.system_prompt_red_lines', { value: JSON.stringify(['不得承诺疗效']) });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.recognition_prompt', { value: '识别昵称、手机号和聊天内容' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.regenerate_max_count', { value: '3' });

    app.unmount();
  });

  it('creates, activates, and tests LLM environments from configuration center', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();

    const llmPanel = [...host.querySelectorAll('.ops-panel')]
      .find((panel) => panel.textContent?.includes('LLM 思考环境')) as HTMLElement;
    expect(llmPanel).toBeTruthy();
    findButton(llmPanel, '新增环境').click();
    await flushSave();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect(drawer.textContent).toContain('LLM 思考环境');
    const textInputs = [...drawer.querySelectorAll('input[type="text"]')] as HTMLInputElement[];
    const passwordInput = drawer.querySelector('input[type="password"]') as HTMLInputElement;
    const numberInputs = [...drawer.querySelectorAll('input[type="number"]')] as HTMLInputElement[];
    const protocolSelect = drawer.querySelector('select') as HTMLSelectElement;
    setInputValue(textInputs[0], 'LLM 测试环境');
    setInputValue(textInputs[1], 'https://llm-test.example.com');
    setInputValue(passwordInput, 'llm-secret-1111');
    setInputValue(textInputs[2], 'qwen-plus');
    setInputValue(protocolSelect, 'OPENAI_COMPATIBLE');
    setInputValue(numberInputs[0], '12000');
    setInputValue(numberInputs[1], '0.4');
    setInputValue(numberInputs[2], '2048');

    findButton(drawer, '保存').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments', {
      envName: 'LLM 测试环境',
      baseUrl: 'https://llm-test.example.com',
      apiKey: 'llm-secret-1111',
      model: 'qwen-plus',
      protocol: 'OPENAI_COMPATIBLE',
      timeoutMs: 12000,
      temperature: 0.4,
      maxTokens: 2048
    });

    const backupCard = [...host.querySelectorAll('.ops-env-card')]
      .find((card) => card.textContent?.includes('LLM 备用')) as HTMLElement;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    findButton(backupCard, '启用').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments/5/activate', {});

    findButton(backupCard, '测试连接').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments/5/test', {});

    app.unmount();
  });

  it('creates, toggles, deletes, and refreshes LLM scene routes', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();

    const routePanel = [...host.querySelectorAll('.ops-panel')]
      .find((panel) => panel.textContent?.includes('LLM 场景路由')) as HTMLElement;
    expect(routePanel).toBeTruthy();
    expect(routePanel.textContent).toContain('LLM 主模型');

    findButton(routePanel, '新增路由').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect(drawer.textContent).toContain('LLM 场景路由');
    const selects = [...drawer.querySelectorAll('select')] as HTMLSelectElement[];
    const priorityInput = drawer.querySelector('input[type="number"]') as HTMLInputElement;
    const enabledInput = drawer.querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect([...selects[0].options].map((option) => option.value)).toContain('FOLLOWUP_SUGGESTION');
    setInputValue(selects[0], 'FOLLOWUP_SUGGESTION');
    setInputValue(selects[1], 'TUAN_GOU');
    setInputValue(selects[2], '5');
    setInputValue(priorityInput, '33');
    enabledInput.checked = true;
    enabledInput.dispatchEvent(new Event('change', { bubbles: true }));

    findButton(drawer, '保存').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-routes', {
      scene: 'FOLLOWUP_SUGGESTION',
      leadType: 'TUAN_GOU',
      environmentId: 5,
      priority: 33,
      enabled: true
    });

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    findButton(routePanel, '停用').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/llm-routes/6/toggle', { enabled: false });

    findButton(routePanel, '删除').click();
    await flushSave();
    expect(apiMocks.deleteJson).toHaveBeenCalledWith('/admin/api/v1/llm-routes/6');

    app.unmount();
  });

  it('rejects invalid clipboard screenshot confirm seconds before saving image runtime config', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushUi();

    const promptInput = [...host.querySelectorAll('label')]
      .find((label) => label.textContent?.includes('截图确认提示停留'))
      ?.querySelector('input') as HTMLInputElement | null;
    expect(promptInput).toBeTruthy();
    setInputValue(promptInput as HTMLInputElement, '2');
    await flushUi();

    const callsBeforeSave = apiMocks.putJson.mock.calls.length;
    findButton(host, '保存识图参数').click();
    await flushSave();

    expect(mainText(host)).toContain('截图确认提示停留必须为 0 或 3-60 秒');
    expect(apiMocks.putJson.mock.calls.length).toBe(callsBeforeSave);

    app.unmount();
  });

  it('protects active or last configuration environments from deletion', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushUi();

    const activeEnv = [...host.querySelectorAll('.ops-env-card')].find((card) => card.textContent?.includes('生产环境')) as HTMLElement;
    const backupEnv = [...host.querySelectorAll('.ops-env-card')].find((card) => card.textContent?.includes('备用环境')) as HTMLElement;
    expect((findButton(activeEnv, '删除') as HTMLButtonElement).disabled).toBe(true);
    expect((findButton(backupEnv, '删除') as HTMLButtonElement).disabled).toBe(false);

    app.unmount();
  });

  it('uses backend pagination for account management instead of loading every account into one page', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '账号与权限').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('当前筛选：42 个账号');
    expect(mainText(host)).toContain('第 1 / 3 页');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/accounts?page=1&page_size=20'));
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('role=LEADER'));
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('is_enabled=1'));

    findButton(host, '下一页').click();
    await flushUi();

    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/accounts?page=2&page_size=20'));

    app.unmount();
  });

  it('renders account actions in a dedicated seventh column after account status', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '账号与权限').click();
    await flushUi();
    await flushUi();

    const rows = [...host.querySelectorAll('.ops-table-row.accounts')];
    expect(rows.length).toBeGreaterThan(1);
    expect([...rows[0].children].map((cell) => cell.textContent?.trim())).toEqual([
      '姓名',
      '手机号',
      '角色',
      '直属组长',
      '最近登录',
      '状态',
      '操作'
    ]);
    const dataCells = [...rows[1].children] as HTMLElement[];
    expect(dataCells).toHaveLength(7);
    expect(dataCells[5].textContent).toContain('启用中');
    expect(dataCells[6].classList.contains('ops-row-actions')).toBe(true);
    expect(dataCells[6].textContent).toContain('重置密码');

    app.unmount();
  });

  it('shows backend failure messages instead of success notices', async () => {
    const { app, host } = await mountConsole();
    apiMocks.putJson.mockResolvedValueOnce({
      success: false,
      data: null,
      errorCode: 'CONFIG_INVALID',
      message: '配置保存失败'
    });

    findSubnavButton(host, '配置中心').click();
    await flushSave();
    findButton(host, '保存配置').click();
    await flushSave();

    expect(host.textContent).toContain('配置保存失败');
    expect(apiMocks.putJson).toHaveBeenCalled();

    app.unmount();
  });

  it('keeps data integration and quick-search workflows on separate subpages', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();

    expect(mainText(host)).toContain('客户数据源');
    expect(mainText(host)).toContain('字段映射');
    expect(mainText(host)).toContain('CSV 导入');
    expect(mainText(host)).not.toContain('速搜内容');
    expect(host.querySelector('.ops-mapping-grid input')).toBeTruthy();
    findButton(host, '识别列名').click();
    await flushUi();
    expect(mainText(host)).toContain('真实表格暂不可用');
    expect(mainText(host)).toContain('取样失败：sheet timeout');
    findButton(host, '对比最新版本').click();
    await flushUi();
    expect(mainText(host)).toContain('新增映射（1）');
    expect(mainText(host)).toContain('phone：mobile -> phone');

    const file = new File(['phone,nickname\n18800000000,张三\n'], 'leads.csv', { type: 'text/csv' });
    const fileInput = host.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fileInput.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();
    await flushUi();

    expect(host.textContent).toContain('预览前');
    findButton(host, '确认导入').click();
    await flushUi();
    expect(apiMocks.postForm).toHaveBeenCalledWith('/admin/api/v1/datasources/import', expect.any(FormData));
    expect(mainText(host)).toContain('总行数 1，新增 1');
    expect(mainText(host)).toContain('last.csv');
    expect(mainText(host)).toContain('phone invalid');

    findButton(host, '保存映射').click();
    await flushUi();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/datasources/10/mappings', {
      mappings: [
        { targetField: 'phone', sourceField: 'phone', enabled: true },
        { targetField: 'nickname', sourceField: 'nickname', enabled: false }
      ]
    });

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    expect(mainText(host)).toContain('速搜内容');
    expect(mainText(host)).toContain('开场话术');
    expect(mainText(host)).not.toContain('客户数据源');
    expect(host.querySelector('.ops-filter-bar input')).toBeTruthy();

    app.unmount();
  });

  it('provides an explicit paginated customer search in data integration', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('客户查询');
    expect(mainText(host)).toContain('王女士');
    const searchInput = [...host.querySelectorAll('input')]
      .find((input) => input.getAttribute('placeholder')?.includes('1111')) as HTMLInputElement;
    setInputValue(searchInput, '1111');
    findButton(host, '查询客户').click();
    await flushUi();

    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/customers/search?q=1111&page=1&page_size=20'));
    findButton(host, '查看档案').click();
    await flushUi();
    expect(mainText(host)).toContain('客户阶段：待确认');
    expect(mainText(host)).toContain('数据来源：私域客资管理表');

    app.unmount();
  });

  it('inserts Chinese customer placeholders into quick-search content', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    findButton(host, '新增内容').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    const variableButtons = [...drawer.querySelectorAll('.ops-variable-bar button')] as HTMLButtonElement[];
    expect(variableButtons.map((button) => button.textContent?.trim())).toEqual([
      '客户昵称', '手机号', '意向门店', '意向项目', '客户阶段', '意向等级',
      '下次跟进时间', '预约日期', '预约项目', '预约门店', '是否到店', '分配管家'
    ]);
    variableButtons.find((button) => button.textContent?.includes('意向等级'))?.click();
    await flushUi();

    expect((drawer.querySelector('textarea') as HTMLTextAreaElement).value).toBe('{{意向等级}}');
    expect(drawer.textContent).toContain('{{客户昵称}}、{{意向门店}}');

    app.unmount();
  });

  it('uses backend pagination and filters for quick-search content', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('当前筛选：21 条速搜内容');
    expect(mainText(host)).toContain('第 1 / 2 页');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/quick-search/items?page=1&size=20'));

    const keywordInput = host.querySelector('.ops-filter-bar input') as HTMLInputElement;
    setInputValue(keywordInput, 'hi');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/quick-search\/items\?.*keyword=hi.*page=1.*size=20/));

    const selects = [...host.querySelectorAll('.ops-filter-bar select')] as HTMLSelectElement[];
    setInputValue(selects[0], 'IMAGE');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/quick-search\/items\?.*contentType=IMAGE.*page=1.*size=20/));
    setInputValue(selects[1], '0');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/quick-search\/items\?.*enabled=false.*page=1.*size=20/));

    findButton(host, '下一页').click();
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/quick-search\/items\?.*enabled=false.*page=2.*size=20/));

    app.unmount();
  });

  it('uses backend pagination for desktop versions', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '版本管理').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('当前筛选：3 个版本');
    expect(mainText(host)).toContain('第 1 / 2 页');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/versions?page=1&size=20'));

    const selects = [...host.querySelectorAll('.ops-filter-bar select')] as HTMLSelectElement[];
    setInputValue(selects[0], 'PUBLISHED');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/versions\?.*status=PUBLISHED.*page=1.*size=20/));
    setInputValue(selects[1], 'MAC');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/versions\?.*platform=MAC.*page=1.*size=20/));

    findButton(host, '下一页').click();
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/versions\?.*status=PUBLISHED.*platform=MAC.*page=2.*size=20/));

    app.unmount();
  });

  it('uses backend pagination for system notices', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '系统公告').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('当前筛选：3 条公告');
    expect(mainText(host)).toContain('第 1 / 2 页');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/notices?page=1&size=20'));

    const selects = [...host.querySelectorAll('.ops-filter-bar select')] as HTMLSelectElement[];
    setInputValue(selects[0], 'STOPPED');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/notices\?.*status=STOPPED.*page=1.*size=20/));
    setInputValue(selects[1], 'WARN');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/notices\?.*level=WARN.*page=1.*size=20/));
    setInputValue(selects[2], 'AUTO');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/notices\?.*source=AUTO.*page=1.*size=20/));

    findButton(host, '下一页').click();
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/notices\?.*status=STOPPED.*level=WARN.*source=AUTO.*page=2.*size=20/));

    app.unmount();
  });

  it('opens organization and system modules as focused subpages', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '账号与权限').click();
    await flushUi();
    expect(mainText(host)).toContain('账号与权限');
    expect(mainText(host)).not.toContain('跟进规则');

    findSubnavButton(host, '跟进规则引擎配置').click();
    await flushUi();
    expect(mainText(host)).toContain('跟进规则');
    expect(mainText(host)).toContain('24 小时未跟进提醒');
    expect(host.querySelector('.ops-filter-bar input')).toBeTruthy();
    const builtinRule = host.querySelector('.ops-rule-card') as HTMLElement;
    expect((findButton(builtinRule, '删除') as HTMLButtonElement).disabled).toBe(true);

    findSubnavButton(host, '客户标签与分层').click();
    await flushUi();
    expect(mainText(host)).toContain('标签与分层');
    expect(mainText(host)).toContain('意向等级');
    expect(mainText(host)).toContain('客户 12 · 规则 2 · 历史 8');

    findSubnavButton(host, '运营分析看板').click();
    await flushUi();
    await flushUi();
    expect(mainText(host)).toContain('运营分析看板');
    expect(mainText(host)).not.toContain('桌面版本');
    expect(mainText(host)).toContain('同事效能');
    expect(mainText(host)).toContain('企微');
    expect(mainText(host)).toContain('张三');
    expect(mainText(host)).toContain('开场话术');
    expect([...host.querySelectorAll('option')].map((option) => option.getAttribute('value'))).toContain('GENERAL');

    findSubnavButton(host, '版本管理').click();
    await flushUi();
    expect(mainText(host)).toContain('桌面版本');
    expect(mainText(host)).toContain('安装包上传');
    expect(mainText(host)).toContain('已撤回');
    expect(mainText(host)).toContain('撤回原因：安装包异常');
    const versionRows = [...host.querySelectorAll('.ops-table-row.version')];
    const publishedRow = versionRows.find((row) => row.textContent?.includes('1.0.0')) as HTMLElement;
    expect((findButton(publishedRow, '发布') as HTMLButtonElement).disabled).toBe(true);
    expect((findButton(publishedRow, '撤回') as HTMLButtonElement).disabled).toBe(false);

    findSubnavButton(host, '系统公告').click();
    await flushUi();
    expect(mainText(host)).toContain('系统公告');
    expect(mainText(host)).toContain('系统自动');
    const autoNotice = [...host.querySelectorAll('.ops-notice-row')].find((row) => row.textContent?.includes('接口异常')) as HTMLElement;
    expect((findButton(autoNotice, '编辑') as HTMLButtonElement).disabled).toBe(true);
    const stoppedNotice = [...host.querySelectorAll('.ops-notice-row')].find((row) => row.textContent?.includes('旧公告')) as HTMLElement;
    expect((findButton(stoppedNotice, '删除') as HTMLButtonElement).disabled).toBe(false);

    findSubnavButton(host, '操作审计日志').click();
    await flushUi();
    expect(mainText(host)).toContain('审计日志');
    expect(mainText(host)).toContain('创建公告');
    expect(mainText(host)).toContain('日志保留 90 天');

    findSubnavButton(host, '系统健康监控').click();
    await flushUi();
    expect(mainText(host)).toContain('系统健康');
    expect(mainText(host)).toContain('数据库');
    expect(mainText(host)).toContain('自动刷新 45 秒');
    expect(mainText(host)).toContain('IMAGE_DOWN');

    app.unmount();
  });

  it('updates accounts with enabled state and leader selection from backend accounts', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '账号与权限').click();
    await flushUi();
    await flushUi();
    findButton(host, '编辑').click();
    await flushUi();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    const textInputs = [...drawer.querySelectorAll('input[type="text"]')] as HTMLInputElement[];
    const selects = [...drawer.querySelectorAll('select')] as HTMLSelectElement[];
    const labels = [...drawer.querySelectorAll('label')].map((label) => label.textContent ?? '');
    expect(labels.some((label) => label.includes('手机号'))).toBe(false);
    expect(labels.some((label) => label.includes('初始密码'))).toBe(false);
    expect(labels.some((label) => label.includes('客户标签管理权限'))).toBe(true);
    expect(drawer.textContent).toContain('手机号不可在编辑中修改');
    setInputValue(textInputs[0], '管理员新名');
    setInputValue(selects[0], 'KEEPER');
    setInputValue(selects[1], '31');
    findButton(drawer, '保存').click();
    await flushUi();

    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/accounts/30', {
      displayName: '管理员新名',
      role: 'KEEPER',
      leaderId: 31,
      isEnabled: true,
      permissions: ['TAG_MANAGEMENT']
    });

    app.unmount();
  });

  it('saves followup rules with backend-executable condition and action config', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '跟进规则引擎配置').click();
    await flushUi();
    await flushUi();
    findButton(host, '新增规则').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    const textInputs = [...drawer.querySelectorAll('input[type="text"]')] as HTMLInputElement[];
    const numberInputs = [...drawer.querySelectorAll('input[type="number"]')] as HTMLInputElement[];
    const selects = [...drawer.querySelectorAll('select')] as HTMLSelectElement[];
    setInputValue(textInputs[0], '高意向超时标签');
    setInputValue(selects[0], 'XIAN_SUO');
    setInputValue(numberInputs[0], '12');
    setInputValue(selects[1], 'TAG_CHANGE');
    setInputValue(selects[2], 'WARN');
    setInputValue(selects[3], 'TAG_SUGGESTION');
    setInputValue(textInputs[1], '高意向待跟进');
    setInputValue(numberInputs[1], '88');
    findButton(drawer, '保存').click();
    await flushUi();

    const payload = apiMocks.postJson.mock.calls.find((call) => call[0] === '/admin/api/v1/rules')?.[1] as Record<string, unknown>;
    expect(payload.actionType).toBe('TAG_CHANGE');
    expect(JSON.parse(String(payload.conditionJson))).toEqual({
      operator: 'AND',
      conditions: [
        { field: 'leadType', op: 'EQ', value: 'XIAN_SUO' },
        { field: 'lastFollowupHours', op: 'GT', value: 12 }
      ]
    });
    expect(JSON.parse(String(payload.actionConfig))).toMatchObject({
      alertLevel: 'WARN',
      reminderType: 'TAG_SUGGESTION',
      tagName: '高意向待跟进'
    });

    app.unmount();
  });

  it('uses backend pagination and batch actions for followup rules', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { app, host } = await mountConsole();

    findSubnavButton(host, '跟进规则引擎配置').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('当前筛选：22 条规则');
    expect(mainText(host)).toContain('第 1 / 2 页');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/rules?page=1&size=20'));

    const filterSelects = [...host.querySelectorAll('.ops-filter-bar select')] as HTMLSelectElement[];
    setInputValue(filterSelects[1], '0');
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('enabled=false'));

    findButton(host, '下一页').click();
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/rules\?.*enabled=false.*page=2.*size=20/));

    const customRule = [...host.querySelectorAll('.ops-rule-card')].find((card) => card.textContent?.includes('高意向标签建议')) as HTMLElement;
    const checkbox = customRule.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();

    findButton(host, '批量停用').click();
    await flushUi();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/rules/41/toggle', { enabled: false });

    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();
    findButton(host, '批量删除').click();
    await flushUi();
    expect(apiMocks.deleteJson).toHaveBeenCalledWith('/admin/api/v1/rules/41');

    app.unmount();
  });

  it('loads category filters, pagination, detail and versioned toggle from tag APIs', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户标签与分层').click();
    await flushSave();

    expect(mainText(host)).toContain('当前筛选：21 个分类');
    expect(mainText(host)).toContain('客户 12 · 规则 2 · 历史 8');
    expect(mainText(host)).toContain('intent_level');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/tags\/categories\?.*merged=false.*page=1.*size=20.*sortBy=sortOrder.*sortDirection=ASC/));

    const filters = host.querySelector('.tag-filters') as HTMLElement;
    setInputValue(filters.querySelector('input') as HTMLInputElement, '意向');
    setInputValue([...filters.querySelectorAll('select')][0] as HTMLSelectElement, 'true');
    setInputValue([...filters.querySelectorAll('select')][2] as HTMLSelectElement, 'updatedAt');
    setInputValue([...filters.querySelectorAll('select')][3] as HTMLSelectElement, 'DESC');
    await flushSave();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/keyword=%E6%84%8F%E5%90%91.*enabled=true.*sortBy=updatedAt.*sortDirection=DESC/));

    const categoryRow = [...host.querySelectorAll('.tag-category-row')].find((row) => row.textContent?.includes('意向等级') && !row.classList.contains('head')) as HTMLElement;
    findButton(categoryRow, '详情').click();
    await flushSave();
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/tags/categories/50');
    expect((host.querySelector('.ops-tag-detail-drawer') as HTMLElement).textContent).toContain('用于判断客户购买意向');
    findButton(host.querySelector('.ops-tag-detail-drawer') as HTMLElement, '关闭').click();
    await flushUi();

    findButton(categoryRow, '编辑').click();
    await flushSave();
    const editDrawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect(controlByLabel<HTMLInputElement>(editDrawer, '系统编号').disabled).toBe(true);
    setInputValue(controlByLabel<HTMLTextAreaElement>(editDrawer, '分类用途'), '更新后的业务用途');
    setInputValue(controlByLabel<HTMLInputElement>(editDrawer, '最低把握程度'), '0.9');
    findButton(editDrawer, '保存').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/tags/categories/50', expect.objectContaining({
      categoryName: '意向等级',
      purpose: '更新后的业务用途',
      selectionMode: 'SINGLE',
      minConfidence: 0.9,
      version: 4
    }));
    expect((apiMocks.putJson.mock.calls.find((call) => call[0] === '/admin/api/v1/tags/categories/50')?.[1] as Record<string, unknown>).categoryKey).toBeUndefined();

    const refreshedCategoryRow = [...host.querySelectorAll('.tag-category-row')].find((row) => row.textContent?.includes('意向等级') && !row.classList.contains('head')) as HTMLElement;
    findButton(refreshedCategoryRow, '停用').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/tags/categories/50/toggle', { enabled: false, version: 4 });
    expect((findButton(refreshedCategoryRow, '删除') as HTMLButtonElement).disabled).toBe(true);

    app.unmount();
  });

  it('uses backend tag names without applying general value translations', async () => {
    const backendCategory = {
      id: 73,
      categoryKey: 'intentLevel',
      categoryName: 'Intent Level',
      selectionMode: 'SINGLE',
      isEnabled: true,
      mergedIntoId: null,
      values: [],
      impact: { customerCount: 0, ruleCount: 0, historyCount: 0 }
    };
    const backendValue = {
      id: 74,
      categoryId: 73,
      categoryKey: 'intentLevel',
      tagValue: 'PENDING',
      displayName: '后端待处理',
      systemSelectable: true,
      manualSelectable: true,
      isEnabled: true,
      mergedIntoId: null,
      impact: { customerCount: 0, ruleCount: 0, historyCount: 0 }
    };
    apiMocks.getJson.mockImplementation(async (path: string) => {
      const data = path.startsWith('/admin/api/v1/tags/categories')
        ? { items: [backendCategory], total: 1, page: 1, size: 20, totalPages: 1 }
        : path.startsWith('/admin/api/v1/tags/values')
          ? { items: [backendValue], total: 1, page: 1, size: 20, totalPages: 1 }
          : apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] };
      return { success: true, data, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户标签与分层').click();
    await flushSave();
    findButton(host, '标签值').click();
    await flushSave();

    const valueRow = host.querySelector('.tag-value-row:not(.head)') as HTMLElement;
    expect(valueRow.textContent).toContain('后端待处理');
    expect(valueRow.textContent).toContain('PENDING');
    expect(valueRow.textContent).toContain('Intent Level');
    expect(valueRow.textContent).not.toContain('待确认');
    expect(valueRow.textContent).not.toContain('意向等级');

    findButton(host, '新增标签值').click();
    await flushUi();
    const categorySelect = controlByLabel<HTMLSelectElement>(host.querySelector('.ops-drawer') as HTMLElement, '分类');
    expect([...categorySelect.options].map((option) => option.textContent)).toContain('Intent Level');
    expect([...categorySelect.options].map((option) => option.textContent)).not.toContain('意向等级');

    app.unmount();
  });

  it('sends an explicitly cleared category purpose with its version', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户标签与分层').click();
    await flushSave();
    const categoryRow = [...host.querySelectorAll('.tag-category-row')]
      .find((row) => row.textContent?.includes('意向等级') && !row.classList.contains('head')) as HTMLElement;
    findButton(categoryRow, '编辑').click();
    await flushSave();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    setInputValue(controlByLabel<HTMLTextAreaElement>(drawer, '分类用途'), '');
    findButton(drawer, '保存').click();
    await flushSave();

    const payload = apiMocks.putJson.mock.calls
      .find((call) => call[0] === '/admin/api/v1/tags/categories/50')?.[1] as Record<string, unknown>;
    expect(payload).toMatchObject({ purpose: '', version: 4 });

    app.unmount();
  });

  it('sends explicitly cleared tag-value text fields, empty synonyms and its version', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户标签与分层').click();
    await flushSave();
    findButton(host, '标签值').click();
    await flushSave();
    const valueRow = [...host.querySelectorAll('.tag-value-row')]
      .find((row) => row.textContent?.includes('高意向') && !row.classList.contains('head')) as HTMLElement;
    findButton(valueRow, '编辑').click();
    await flushSave();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    for (const label of ['标签含义', '适用条件', '禁止条件', '正确例子', '错误例子', '同义表达']) {
      setInputValue(controlByLabel<HTMLTextAreaElement>(drawer, label), '');
    }
    findButton(drawer, '保存').click();
    await flushSave();

    const payload = apiMocks.putJson.mock.calls
      .find((call) => call[0] === '/admin/api/v1/tags/values/51')?.[1] as Record<string, unknown>;
    expect(payload).toMatchObject({
      meaning: '',
      applicableWhen: '',
      notApplicableWhen: '',
      positiveExamples: '',
      negativeExamples: '',
      synonyms: [],
      version: 7
    });

    app.unmount();
  });

  it('creates categories and edits every tag-value business field without accepting internal codes', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户标签与分层').click();
    await flushSave();
    findButton(host, '新增分类').click();
    await flushUi();
    let drawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect([...drawer.querySelectorAll('.ops-label-title')].some((label) => label.textContent?.includes('系统编号'))).toBe(false);
    expect([...drawer.querySelectorAll('.ops-label-title')].some((label) => label.textContent?.includes('绑定客户档案字段'))).toBe(false);
    setInputValue(controlByLabel<HTMLInputElement>(drawer, '分类名称'), '客户阶段');
    setInputValue(controlByLabel<HTMLTextAreaElement>(drawer, '分类用途'), '用于运营阶段判断');
    setInputValue(controlByLabel<HTMLSelectElement>(drawer, '选择模式'), 'MULTI');
    findButton(drawer, '保存').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/tags/categories', expect.objectContaining({
      categoryName: '客户阶段',
      purpose: '用于运营阶段判断',
      selectionMode: 'MULTI',
      autoUpdateMode: 'RECORD_ONLY',
      minConfidence: 0.85,
      useForFollowupRules: true,
      sortOrder: 99
    }));
    const createPayload = apiMocks.postJson.mock.calls.find((call) => call[0] === '/admin/api/v1/tags/categories')?.[1] as Record<string, unknown>;
    expect(createPayload.categoryKey).toBeUndefined();
    expect(createPayload.boundField).toBeUndefined();

    findButton(host, '标签值').click();
    await flushSave();
    expect(mainText(host)).toContain('当前筛选：22 个标签值');
    const valueFilters = host.querySelector('.tag-value-filters') as HTMLElement;
    const valueFilterSelects = [...valueFilters.querySelectorAll('select')] as HTMLSelectElement[];
    setInputValue(valueFilters.querySelector('input') as HTMLInputElement, '高意向');
    setInputValue(valueFilterSelects[0], '50');
    setInputValue(valueFilterSelects[1], 'true');
    setInputValue(valueFilterSelects[3], 'updatedAt');
    setInputValue(valueFilterSelects[4], 'DESC');
    await flushSave();
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/tags\/values\?.*categoryId=50.*keyword=%E9%AB%98%E6%84%8F%E5%90%91.*enabled=true.*merged=false.*sortBy=updatedAt.*sortDirection=DESC/));
    const valueRow = [...host.querySelectorAll('.tag-value-row')].find((row) => row.textContent?.includes('高意向') && !row.classList.contains('head')) as HTMLElement;
    findButton(valueRow, '编辑').click();
    await flushSave();
    drawer = host.querySelector('.ops-drawer') as HTMLElement;
    const internalCode = controlByLabel<HTMLInputElement>(drawer, '系统编号');
    expect(internalCode.disabled).toBe(true);
    expect(internalCode.value).toBe('high_intent');
    setInputValue(controlByLabel<HTMLInputElement>(drawer, '标签名称'), '高意向客户');
    setInputValue(controlByLabel<HTMLTextAreaElement>(drawer, '标签含义'), '两周内有明确购买计划');
    setInputValue(controlByLabel<HTMLTextAreaElement>(drawer, '同义表达'), '近期购买\n准备到店');
    findButton(drawer, '保存').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/tags/values/51', {
      categoryId: 50,
      displayName: '高意向客户',
      meaning: '两周内有明确购买计划',
      applicableWhen: '主动询价并确认到店时间',
      notApplicableWhen: '仅咨询基础信息',
      positiveExamples: '本周可以到店体验吗',
      negativeExamples: '先了解一下',
      systemSelectable: true,
      manualSelectable: true,
      isEnabled: true,
      sortOrder: 10,
      version: 7,
      synonyms: ['近期购买', '准备到店']
    });

    app.unmount();
  });

  it('previews and executes a versioned value merge, exports CSV and displays delete protection errors', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:tags');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    apiMocks.postJson.mockImplementation(async (path: string) => path.endsWith('/merge-preview')
      ? {
          success: true,
          data: {
            sourceName: '高意向',
            targetName: '中意向',
            impact: { customerCount: 9, ruleCount: 1, historyCount: 5 },
            valueCount: 0,
            codeConflictCount: 0,
            warnings: ['将更新 9 位客户的标签引用']
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: {}, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户标签与分层').click();
    await flushSave();
    findButton(host, '标签值').click();
    await flushSave();
    const valueRow = [...host.querySelectorAll('.tag-value-row')].find((row) => row.textContent?.includes('高意向') && !row.classList.contains('head')) as HTMLElement;
    findButton(valueRow, '合并').click();
    await flushSave();
    await flushSave();
    const mergeDrawer = host.querySelector('.ops-tag-merge-drawer') as HTMLElement;
    const targetSelect = mergeDrawer.querySelector('select') as HTMLSelectElement;
    expect([...targetSelect.options].map((option) => option.value)).toContain('52');
    setInputValue(targetSelect, '52');
    await flushUi();
    findButton(mergeDrawer, '生成合并预览').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/tags/values/51/merge-preview', {
      targetId: 52,
      sourceVersion: 7,
      targetVersion: 3
    });
    expect(mergeDrawer.textContent).toContain('将更新 9 位客户的标签引用');
    findButton(mergeDrawer, '确认合并').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/tags/values/51/merge', {
      targetId: 52,
      sourceVersion: 7,
      targetVersion: 3
    });

    findButton(host, '导出 CSV').click();
    await flushSave();
    expect(apiMocks.getBlob).toHaveBeenCalledWith(expect.stringMatching(/\/admin\/api\/v1\/tags\/values\/export\?.*merged=false.*sortBy=sortOrder.*sortDirection=ASC/));

    apiMocks.deleteJson.mockResolvedValueOnce({
      success: false,
      data: null,
      errorCode: '90-10004',
      message: '该标签仍影响 9 位客户、1 条规则和 5 条历史记录，只能停用或合并'
    });
    const refreshedValueRow = [...host.querySelectorAll('.tag-value-row')].find((row) => row.textContent?.includes('高意向') && !row.classList.contains('head')) as HTMLElement;
    findButton(refreshedValueRow, '删除').click();
    await flushSave();
    expect(mainText(host)).toContain('只能停用或合并');

    app.unmount();
  });

  it('limits delegated tag managers to the tag page and tag endpoints only', async () => {
    const { app, host } = await mountConsole({ accountName: '组长', tagManagementOnly: true });
    await flushSave();

    expect([...host.querySelectorAll('.ops-admin-subnav-button small')].map((item) => item.textContent)).toEqual(['客户标签与分层']);
    expect(host.textContent).not.toContain('账号与权限');
    expect(host.textContent).not.toContain('跟进规则引擎配置');
    const requestedPaths = apiMocks.getJson.mock.calls.map((call) => String(call[0]));
    expect(requestedPaths.length).toBeGreaterThan(0);
    expect(requestedPaths.every((path) => path.startsWith('/admin/api/v1/tags/'))).toBe(true);

    apiMocks.getJson.mockClear();
    const { eventBus } = await import('../../shared/eventBus');
    eventBus.emit('CONFIG_REFRESH', { configKeys: ['tag_config'] });
    await flushSave();
    expect(apiMocks.getJson.mock.calls.length).toBeGreaterThan(0);
    expect(apiMocks.getJson.mock.calls.every((call) => String(call[0]).startsWith('/admin/api/v1/tags/'))).toBe(true);

    app.unmount();
  });

  it('uploads a desktop version package and fills the version form', async () => {
    apiMocks.postForm.mockResolvedValueOnce({ success: true, data: { downloadUrl: '/downloads/app.exe', fileSize: 2048 }, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '版本管理').click();
    await flushUi();
    findButton(host, '新增版本').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    const file = new File(['package-content'], 'app.exe', { type: 'application/octet-stream' });
    const fileInput = drawer.querySelector('.ops-upload-box input[type="file"]') as HTMLInputElement;
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fileInput.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();
    await flushUi();

    expect(apiMocks.postForm).toHaveBeenCalledWith('/admin/api/v1/versions/upload', expect.any(FormData), 120000);
    const textValues = [...drawer.querySelectorAll('input[type="text"]')].map((input) => (input as HTMLInputElement).value);
    expect(textValues).toContain('/downloads/app.exe');
    expect(drawer.textContent).toContain('上传完成');

    app.unmount();
  });

  it('keeps analytics sections usable when one analytics endpoint fails', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      if (path.startsWith('/admin/api/v1/analytics/sources')) {
        throw new Error('sources timeout');
      }
      return { success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '运营分析看板').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('部分分析区块刷新失败');
    expect(mainText(host)).toContain('客户来源：请求超时，请稍后重试');
    expect(mainText(host)).toContain('同事效能');
    expect(mainText(host)).toContain('张三');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/accounts?page=1&page_size=50'));

    app.unmount();
  });

  it('filters and exports audit logs with multiple selected actions', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '操作审计日志').click();
    await flushUi();
    const targetIdInput = [...host.querySelectorAll('input')]
      .find((input) => input.getAttribute('placeholder') === '对象 ID') as HTMLInputElement;
    setInputValue(targetIdInput, 'notice-1');
    await flushUi();
    const chips = [...host.querySelectorAll('.audit-action-chip')] as HTMLLabelElement[];
    expect(chips).toHaveLength(2);
    (chips[0].querySelector('input') as HTMLInputElement).click();
    await flushUi();
    (chips[1].querySelector('input') as HTMLInputElement).click();
    await flushUi();
    findButton(host, '导出 CSV').click();
    await flushUi();

    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('action=CREATE_NOTICE%2CUPDATE_NOTICE'));
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('targetId=notice-1'));
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/audit-logs/export', expect.objectContaining({
      action: 'CREATE_NOTICE,UPDATE_NOTICE',
      targetId: 'notice-1'
    }));
    expect(mainText(host)).toContain('动作：创建公告、编辑公告，对象：全部 notice-1');

    app.unmount();
  });

  it('downloads audit exports with the saved bearer token', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('id,action\n1,CREATE_NOTICE\n', { status: 200 }));
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:csv');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    apiMocks.postJson.mockResolvedValueOnce({
      success: true,
      data: { exportId: 'exp_done', status: 'DONE', downloadUrl: '/admin/api/v1/audit-logs/export/exp_done/download' },
      errorCode: null,
      message: null
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '操作审计日志').click();
    await flushUi();
    findButton(host, '导出 CSV').click();
    await flushUi();
    findButton(host, '下载 CSV').click();
    await flushUi();

    expect(fetchSpy).toHaveBeenCalledWith('http://localhost:8080/admin/api/v1/audit-logs/export/exp_done/download', {
      headers: { Authorization: 'Bearer token-a' }
    });
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:csv');

    app.unmount();
  });

  it('shows confirmation and failure feedback for dangerous operations', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    apiMocks.deleteJson.mockRejectedValueOnce(new Error('删除失败'));
    const { app, host } = await mountConsole();

    findButton(host, '删除').click();
    await flushUi();
    await flushUi();

    expect(window.confirm).toHaveBeenCalled();
    expect(apiMocks.deleteJson).toHaveBeenCalledWith('/admin/api/v1/skills/1');
    expect(host.textContent).toContain('删除失败');

    app.unmount();
  });
});
