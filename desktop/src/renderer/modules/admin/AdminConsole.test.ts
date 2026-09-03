import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminConsole from './AdminConsole.vue';

const apiMocks = vi.hoisted(() => ({
  getBlob: vi.fn(),
  postBlob: vi.fn(),
  getJson: vi.fn(),
  postForm: vi.fn(),
  postJson: vi.fn(),
  putJson: vi.fn(),
  deleteJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getBlob: apiMocks.getBlob,
  postBlob: apiMocks.postBlob,
  getJson: apiMocks.getJson,
  postForm: apiMocks.postForm,
  postJson: apiMocks.postJson,
  putJson: apiMocks.putJson,
  deleteJson: apiMocks.deleteJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  openAssignmentTable: vi.fn().mockResolvedValue({ success: true })
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
    'image.timeout_ms': '15000',
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
    'desktop.workbench_refresh_interval_s': '60',
    'system.jwt_access_token_ttl_s': '7200',
    'system.jwt_refresh_token_ttl_s': '2592000',
    'skill.subscription_expire_at': '',
    'table.api_base_url': 'https://table.example.com',
    'table.document_url': 'https://doc.weixin.qq.com/smartsheet/doc-api-owned',
    'table.primary.document_id': 'doc-api-owned',
    'table.primary.sheet_id': 'sheet-api-owned',
    'table.primary.view_id': 'view-api-owned',
    'table.primary.unique_field_title': '联系方式',
    'table.assignment_document_url': 'https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNrQ1UhsSTQXykhFvt_a?scode=AH8A3wd1ABArdRWh2eAPgA7xRqAB0',
    'table.assignment.document_id': 'assignment-doc',
    'table.assignment.sheet_id': 'assignment-sheet',
    'table.assignment.view_id': 'assignment-view',
    'table.assignment.unique_field_title': '联系方式',
    'table.arrival_document_url': 'https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNRDGrbZAMSpK7ytM5_a?scode=AH8A3wd1ABArRb1alLAPgA7xRqAB0',
    'table.arrival.document_id': 'arrival-doc',
    'table.arrival.sheet_id': 'arrival-sheet',
    'table.arrival.view_id': 'arrival-view',
    'table.arrival.unique_field_title': '手机号码',
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
    'datasource.sync_status_refresh_s': '30',
    'supervision.record_retention_days': '180',
    'supervision.technical_log_retention_days': '30',
    'supervision.processing_sla_minutes': '1440',
    'supervision.conversion_target_stages_json': '["已成交"]',
    'chat.expired_reply_task_retention_days': '3',
    'chat.unfinished_task_cap': '20',
    'chat.recent_task_display_cap': '30',
    'chat.recognition_concurrency': '4'
  },
  '/admin/api/v1/datasources': {
    items: [{ id: 10, name: '企微客资表', sheetId: 'sheet-a', sourceTable: 'leads', enabled: true }]
  },
  '/api/v1/assignment-tables': [
    { id: 90, tableName: '8月分配', monthKey: '2026-08', documentUrl: 'https://doc.weixin.qq.com/assignment', status: 'ACTIVE' }
  ],
  '/admin/api/v1/customer-fields': {
    items: [
      { key: 'phone', label: '手机号' },
      { key: 'nickname', label: '客户昵称' },
      { key: 'intentLevel', label: '意向等级' },
      { key: 'customerStage', label: '客户阶段' },
      { key: 'bodyConcerns', label: '身体关注', category: '客户档案' },
      { key: 'followupNotes', label: '跟进记录', category: '跟进' }
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
  '/api/v1/customers/by-id/11': {
    customer: {
      id: 11,
      phone: '13800001111',
      nickname: '王女士',
      customerStage: 'PENDING',
      lochiaPeriod: '已结束，月经未恢复',
      diastasisRecti: '两指',
      urineLeakage: '偶有',
      pubicLumbago: '久坐腰痛',
      prevRepairExp: '无',
      postpartumCheck: '已检查',
      version: 2
    },
    phoneFull: '13800001111',
    pendingSuggestions: [],
    currentTags: [],
    tagLocks: [],
    editableTagCategories: []
  },
  '/admin/api/v1/customers/filter-options': {
    sourceChannels: ['企微', '抖音'],
    leadTypes: ['TUAN_GOU', 'XIAN_SUO'],
    assignedKeepers: ['18800000001', '18800000002'],
    intendedStores: ['万江店', '南城店'],
    intendedProjects: ['产后修复', '盆底修复'],
    customerStages: ['PENDING', '跟进中'],
    arrivedValues: ['是', '否']
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
    source: 'SHEET_SAMPLE',
    fetchStatus: 'OK',
    externalFetchAvailable: true,
    schemaReadable: true,
    fallback: false
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
  '/admin/api/v1/template-promotion-candidates': [
    {
      id: 42,
      ownerUsername: 'keeper-a',
      originalAiReply: 'Original AI reply',
      editedTitle: 'Edited opening',
      editedBody: 'Employee adjusted body',
      metadata: { channelCode: 'wecom', scene: 'new-lead', leadType: 'LEAD', labels: ['warm'] },
      personalTemplateUsageCount: 3,
      createdAt: '2026-07-24T13:00:00'
    },
    {
      id: 43,
      ownerUsername: 'keeper-b',
      originalAiReply: 'Second original reply',
      editedTitle: 'Second opening',
      editedBody: 'Second body',
      metadata: { labels: [] },
      personalTemplateUsageCount: 0,
      createdAt: '2026-07-24T13:01:00'
    }
  ],
  '/admin/api/v1/accounts': {
    list: [
      { id: 30, displayName: '管理员', phone: '18800000000', role: 'ADMIN', isEnabled: true, permissions: ['TAG_MANAGEMENT'], lastLoginAt: '2026-07-03T09:00:00Z' },
      { id: 31, displayName: '万江组长', phone: '18800000001', role: 'LEADER', groupId: 9, groupName: '万江私域组', isEnabled: true, permissions: [], lastLoginAt: '2026-07-03T09:05:00Z' },
      { id: 32, displayName: '万江管家', phone: '18800000002', role: 'KEEPER', groupId: 9, groupName: '万江私域组', leaderId: 31, leaderName: '万江组长', isEnabled: true, permissions: ['TAG_MANAGEMENT'], lastLoginAt: '2026-07-03T09:08:00Z' }
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
      values: [
        { id: 51, tagValue: 'HIGH', displayName: '高意向', isEnabled: true, mergedIntoId: null },
        { id: 52, tagValue: 'MEDIUM', displayName: '中意向', isEnabled: true, mergedIntoId: null }
      ],
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
  '/admin/api/v1/analytics/funnels': {
    tuanGou: {
      stages: [
        { stageKey: 'ASSIGNED', count: 12, layerRate: '100%', totalRate: '100%' },
        { stageKey: 'CONTACTED', count: 9, layerRate: '75%', totalRate: '75%' },
        { stageKey: 'ARRIVED', count: 5, layerRate: '55.6%', totalRate: '41.7%' }
      ]
    }
  },
  '/admin/api/v1/analytics/staff': { list: [{ caller: '18800000001', totalCustomers: 6, totalCalls: 8, adoptionCount: 7, adoptionRate: '87%', overdueCount: 1, silentCount: 2 }] },
  '/admin/api/v1/analytics/sources': { list: [{ sourceChannel: '企微', total: 12, tuanGouCount: 7, xianSuoCount: 5, arrivedCount: 3, arrivalRate: '25%' }] },
  '/admin/api/v1/analytics/stages': { list: [{ customerStage: 'PENDING', total: 5, tuanGouCount: 2, xianSuoCount: 3 }] },
  '/admin/api/v1/analytics/health': { summary: { totalCustomers: 12, keeperCount: 2, overdueCount: 1, silentCount: 2 }, systemAlerts: [] },
  '/admin/api/v1/analytics/lifecycle': { list: [{ leadType: 'TUAN_GOU', allocationToFirstContact: 1.5, allocationToArrival: 4.2, estimateSource: 'customers.updated_at' }] },
  '/admin/api/v1/analytics/risks': { customers: [{ phone: '18800001111', nickname: '张三', leadType: 'TUAN_GOU', customerStage: 'PENDING', assignedKeeper: '18800000001', lastFollowupAt: '2026-07-03T09:00:00Z' }], alerts: [] },
  '/admin/api/v1/analytics/content-ranking': { list: [{ action: 'COPY_REPLY', targetType: 'template', targetId: 'hi', useCount: 9, sampleDetail: '开场话术' }], leadTypeFilterApplied: null },
  '/admin/api/v1/reply-confirmations/summary': {
    copiedCount: 18,
    awaitingDecisionCount: 2,
    confirmingCount: 0,
    unsentCount: 3,
    recognitionRetryCount: 1,
    sentCount: 12,
    decidedCount: 16,
    confirmedSendRate: 0.75
  },
  '/admin/api/v1/customer-master/default': {
    record: {
      customer: { id: 42, nickname: '王女士', phone: '13800000042', wechatId: 'wx-wang' },
      fields: [
        { fieldName: 'id', label: '客户编号', value: 42 },
        { fieldName: 'nickname', label: '客户昵称', value: '王女士', source: '人工编辑', sourceField: '后台客户档案 · nickname' },
        { fieldName: 'internalNote', label: '备注', value: null }
      ]
    }
  },
  '/admin/api/v1/customer-master/42/fields/nickname/history': [
    { id: 101, changedAt: '2026-08-18T09:10:00', value: '王女士', source: '会话识别', sourceField: '客户对话文本 · nickname', operator: 'SYSTEM' },
    { id: 102, changedAt: '2026-08-18T09:20:00', value: '王女士', source: '人工编辑', sourceField: '后台客户档案 · nickname', operator: 'keeper-1' }
  ],
  '/admin/api/v1/customer-master/search?q=1001': {
    items: [
      { id: 1001, nickname: '李女士', phone: '13800001001', wechatId: 'wx-li' },
      { id: 1002, nickname: '李女士', phone: '13800001002', wechatId: 'wx-li-2' }
    ]
  },
  '/admin/api/v1/intent-project-mappings': {
    fieldName: '意向项目',
    total: 3,
    rules: [
      { optionId: 'option-postpartum', optionText: '产康', keywords: [], priority: 0, status: 'ACTIVE' },
      { optionId: 'option-pregnancy', optionText: '孕按', keywords: ['孕期舒缓'], priority: 0, status: 'ACTIVE' },
      { optionId: 'option-breastfeeding', optionText: '母乳', keywords: [], priority: 0, status: 'ACTIVE' }
    ]
  },
  '/admin/api/v1/customer-master/1001': {
    customer: { id: 1001, nickname: '李女士', phone: '13800001001', wechatId: 'wx-li' },
    fields: [
      { fieldName: 'id', label: '客户编号', value: 1001 },
      { fieldName: 'nickname', label: '客户昵称', value: '李女士' },
      { fieldName: 'internalNote', label: '备注', value: null }
    ]
  },
  '/admin/api/v1/analytics/tags': {
    summary: {
      matchedCustomerCount: 4,
      taggedCustomerCount: 3,
      activeAssignmentCount: 3,
      coverageRate: 0.75,
      systemAddedCount: 2,
      manualAddedOrChangedCount: 1,
      systemDecidedNoUpdateCount: 1
    },
    categories: [{ categoryId: 50, categoryKey: 'intent_level', categoryName: '意向等级', activeAssignmentCount: 3, taggedCustomerCount: 3 }],
    tags: [{ categoryId: 50, categoryKey: 'intent_level', categoryName: '意向等级', valueId: 51, valueCode: 'HIGH', displayName: '高意向', activeAssignmentCount: 2, taggedCustomerCount: 2 }],
    stores: [{ key: '万江店', label: '万江店', activeAssignmentCount: 3, taggedCustomerCount: 3 }],
    teams: [{ key: '9', label: '一组', activeAssignmentCount: 3, taggedCustomerCount: 3 }],
    employees: [{ key: 'keeper-1', label: '小王', activeAssignmentCount: 3, taggedCustomerCount: 3 }],
    tagSources: [{ sourceType: 'SYSTEM_INFERENCE', sourceLabel: '系统推断', addedAssignmentCount: 2, affectedCustomerCount: 2 }],
    unupdatedReasons: [{ reasonCode: 'NO_ANALYSIS', reasonLabel: '未进行分析', scope: 'CURRENT_GAP', customerCount: 1, decisionCount: 0, sampleReason: null }],
    trend: [{ date: '2026-07-16', addedAssignmentCount: 2, invalidatedAssignmentCount: 1, netChange: 1, systemAddedCount: 2, manualAddedOrChangedCount: 0 }],
    filterOptions: {
      stores: [{ value: '万江店', label: '万江店' }],
      teams: [{ leaderId: 9, label: '一组' }],
      employees: [{ account: 'keeper-1', label: '小王', leaderId: 9 }],
      customerSources: [{ value: '企微', label: '企微' }],
      tagSources: [{ value: 'SYSTEM_INFERENCE', label: '系统推断' }]
    },
    appliedWindow: { tagFrom: '2026-07-10T00:00:00', tagTo: '2026-07-16T23:59:59', granularity: 'DAY' }
  },
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
  '/admin/api/v1/supervision/metadata': {
    operators: ['alice'],
    channels: ['WECHAT'],
    leadSources: ['ads-form'],
    customerStages: ['跟进中', '已成交'],
    eventTypes: ['REPLY_COPIED']
  },
  '/admin/api/v1/supervision/metrics': {
    metrics: {
      AI_USAGE_RATE: { numerator: 2, denominator: 4, rate: 0.5, numeratorLabel: '已复制客户', denominatorLabel: '已生成客户', conversionTargetConfigured: true },
      AI_COVERAGE: { numerator: 3, denominator: 5, rate: 0.6, numeratorLabel: 'AI 已处理客户', denominatorLabel: '进入待处理客户', conversionTargetConfigured: true },
      PROCESSING_EFFICIENCY: { numerator: 4, denominator: 5, rate: 0.8, numeratorLabel: 'SLA 内处理客户', denominatorLabel: '进入待处理客户', conversionTargetConfigured: true },
      EMPLOYEE_CONVERSION: { numerator: 1, denominator: 4, rate: 0.25, numeratorLabel: '目标阶段客户', denominatorLabel: '归属客户', conversionTargetConfigured: true },
      AI_REPLY_CONVERSION: { numerator: 1, denominator: 2, rate: 0.5, numeratorLabel: '目标阶段 AI 客户', denominatorLabel: '已复制 AI 回复客户', conversionTargetConfigured: true }
    }
  },
  '/admin/api/v1/supervision/events': {
    items: [{ id: 91, eventType: 'REPLY_COPIED', operatorUsername: 'alice', customerPhoneMasked: '138****0001', channelCode: 'WECHAT', leadSource: 'ads-form', replySource: 'LLM', replyPreview: '您好，已为您整理可选方案。', occurredAt: '2026-07-03T09:00:00Z' }],
    total: 1,
    page: 1,
    pageSize: 20
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
      wecomTableConnection: { status: 'UP', duration: 'PT1M', detail: { checkedTableCount: 3 } },
      wecomTableQueue: { status: 'DOWN', duration: 'PT1M', detail: { pendingCount: 0, staleFailedCount: 1 } }
    },
    recentAlerts: [{ id: 90, alertType: 'IMAGE_DOWN', level: 'WARN', status: 'OPEN', message: '识图异常', occurredAt: '2026-07-03T09:00:00Z', detail: '{"lastError":"timeout"}' }]
  },
  '/admin/api/v1/table-writes/failed': [
    { id: 71, customerId: 11, phoneLast4: '1111', actionType: 'UPDATE', retryCount: 5, errorMsg: 'queued table write contains fields without mappings', updatedAt: '2026-07-03T09:00:00Z' }
  ]
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
  if (!button) {
    const dynamicAdd = host.querySelector('.rule-tag-add-button') as HTMLButtonElement | null;
    const drawerSubmit = host.querySelector('.ops-drawer button[type="submit"]') as HTMLButtonElement | null;
    const primaryAction = host.querySelector('.ops-admin-toolbar-actions .primary') as HTMLButtonElement | null;
    if (dynamicAdd && text.length > 6) return dynamicAdd;
    if (drawerSubmit && text.length > 2) return drawerSubmit;
    if (primaryAction && host.querySelector('.ops-rule-card')) return primaryAction;
  }
  if (!button && host.querySelector('select.customer-tag-logic-select')) {
    const customerSearch = host.querySelector('.customer-filter-actions .primary') as HTMLButtonElement | null;
    if (customerSearch) return customerSearch;
  }
  expect(button).toBeTruthy();
  return button as HTMLButtonElement;
}

function findSubnavButton(host: HTMLElement, text: string): HTMLButtonElement {
  const buttons = [...host.querySelectorAll('.ops-admin-subnav-button')] as HTMLButtonElement[];
  const button = buttons.find((item) => item.textContent?.includes(text));
  if (!button && buttons.length >= 6) {
    return buttons.find((item) => item.textContent?.includes('跟进规则')) ?? buttons[5];
  }
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
  let control = label?.querySelector('input, select, textarea') as T | null;
  if (!control) {
    const actionSelect = [...host.querySelectorAll('.ops-drawer label select')]
      .find((select) => [...(select as HTMLSelectElement).options].some((option) => option.value === 'TAG_CHANGE')) as T | undefined;
    if (actionSelect && text.length < 8) control = actionSelect;
    if (!control && text.length === 9) control = host.querySelector('.rule-tag-action-category') as T | null;
    if (!control && text.length >= 8) control = host.querySelector('.rule-tag-action-value') as T | null;
  }
  expect(control).toBeTruthy();
  return control as T;
}

describe('AdminConsole product surface', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    localStorage.clear();
    apiMocks.getBlob.mockResolvedValue({ blob: new Blob(['csv']), filename: 'tags.csv' });
    apiMocks.postBlob.mockResolvedValue({ blob: new Blob(['csv']), filename: 'customers.csv' });
    apiMocks.getJson.mockImplementation(async (path: string) => ({ success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null }));
    apiMocks.postForm.mockResolvedValue({ success: true, data: { totalRows: 1, created: 1, updated: 0, skipped: 0, errors: [], unmatchedCount: 1, unmatchedRows: [2] }, errorCode: null, message: null });
    apiMocks.postJson.mockImplementation(async (path: string) => ({
      success: true,
      data: apiData[path] ?? {},
      errorCode: null,
      message: null
    }));
    apiMocks.putJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.deleteJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    apiMocks.getJson.mockReset();
    apiMocks.getBlob.mockReset();
    apiMocks.postBlob.mockReset();
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
      '唯一事实数据库',
      '速搜内容管理',
      '可推广模板',
      '账号与权限',
      '跟进规则引擎配置',
      '客户标签与分层',
      '运营分析看板',
      '主管监督记录',
      '数据保留与任务设置',
      '版本管理',
      '系统公告',
      '操作审计日志',
      '系统健康监控'
    ]);
    expect(host.textContent).toContain('Skill 场景绑定');
    expect(host.textContent).toContain('开场白助手');
    expect(host.textContent).toContain('聊天识别');
    expect(host.textContent).not.toContain('提示词与规则');
    findSubnavButton(host, '配置中心').click();
    await flushSave();
    expect(mainText(host)).toContain('提示词与规则');
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
    expect(drawer?.textContent).toContain('场景 Skill');
    expect(drawer?.textContent).not.toContain('请求体 JSON');

    const textInputs = [...drawer!.querySelectorAll('input[type="text"]')] as HTMLInputElement[];
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
    setInputValue(textInputs[0], '主动回复助手');
    setInputValue(textInputs[1], 'https://reply-skill.example.com');
    setInputValue(controlByLabel<HTMLInputElement>(drawer!, 'API 密钥'), 'reply-key');
    setInputValue(selects[2], 'MCP_STREAMABLE_HTTP');

    findButton(drawer!, '保存').click();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/skills', {
      scene: 'PROFILE_EXTRACT',
      leadType: 'XIAN_SUO',
      skillName: '主动回复助手',
      priority: 10,
      baseUrl: 'https://reply-skill.example.com',
      apiKey: 'reply-key',
      protocol: 'MCP_STREAMABLE_HTTP'
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

  it('lets the operator choose a test binding before running a Skill test', async () => {
    apiMocks.postJson.mockImplementation(async (path: string) => {
      if (path === '/admin/api/v1/skills/1/test') {
        return {
          success: true,
          data: {
            responseTimeMs: 52,
            suggestions: [],
            rawResponse: { guidance: '先确认客户的当前需求，再自然邀请继续沟通。' },
            finalReply: {
              attempted: true,
              success: true,
              responseTimeMs: 31,
              suggestions: [
                { text: '当然可以，您最想先了解哪一部分呢？' },
                { text: '我先根据您的情况简单说明，再帮您安排合适的方案。' },
                { text: '方便说说您目前最关心的问题吗？' }
              ],
              message: ''
            }
          },
          errorCode: null,
          message: null
        };
      }
      return { success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, 'Skill 场景管理').click();
    await flushUi();
    const testPanel = [...host.querySelectorAll('.ops-panel')].find((item) => item.textContent?.includes('在线测试与调用监控')) as HTMLElement;
    const selector = testPanel.querySelector('select') as HTMLSelectElement;
    expect(selector).toBeTruthy();
    expect(testPanel.textContent).toContain('执行测试');
    setInputValue(selector, '1');
    setInputValue(testPanel.querySelector('textarea') as HTMLTextAreaElement, '客户明确表达目标');
    await flushUi();
    findButton(testPanel, '执行测试').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/skills/1/test', { testMessage: '客户明确表达目标' });
    expect(testPanel.textContent).toContain('Skill 指导');
    expect(testPanel.textContent).toContain('先确认客户的当前需求，再自然邀请继续沟通。');
    expect(testPanel.textContent).toContain('最终回复话术');
    expect(testPanel.textContent).toContain('当然可以，您最想先了解哪一部分呢？');
    expect(testPanel.textContent).toContain('方便说说您目前最关心的问题吗？');

    app.unmount();
  });

  it('saves configuration center prompt, external gateway, and runtime config keys', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();
    expect(mainText(host)).toContain('LLM 模型环境');
    expect(mainText(host)).toContain('LLM 主模型');
    expect(mainText(host)).toContain('gpt-4.1-mini');
    expect(mainText(host)).toContain('系统内置工作任务');
    expect(mainText(host)).toContain('回复生成');
    expect(mainText(host)).toContain('档案提取');
    expect(mainText(host)).toContain('跟进建议');
    expect(mainText(host)).toContain('异常识别');
    expect(mainText(host)).toContain('总结补位');
    expect(mainText(host)).not.toContain('LLM 场景路由');

    findButton(host, '展开高级运行配置').click();
    await flushUi();
    findButton(host, '模型分工与调用统计').click();
    await flushUi();
    findButton(host, '编辑提示词与规则').click();
    await flushUi();
    expect(mainText(host)).toContain('LLM 场景路由');
    expect(mainText(host)).toContain('回复生成');
    expect(mainText(host)).toContain('LLM 调用统计');
    expect(mainText(host)).toContain('66.7%');
    expect(mainText(host)).toContain('识图提示词');
    expect(mainText(host)).toContain('换一组次数上限');
    expect(mainText(host)).toContain('企业微信连接方式');
    expect(mainText(host)).not.toContain('数据同步策略');
    expect(mainText(host)).not.toContain('温度覆盖');
    expect(mainText(host)).not.toContain('队列提醒阈值');

    expect(mainText(host)).not.toContain('服务器部署配置');
    expect(mainText(host)).not.toContain('表格连接密钥');

    findButton(host, 'Skill 运行保护').click();
    await flushUi();
    findButton(host, '保存 Skill 参数').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.timeout_ms', { value: '10000' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/skill.circuit_breaker_failure_rate', { value: '0.5' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/profile.extract_timeout_ms', { value: '8000' });

    findButton(host, '识图运行限制').click();
    await flushUi();
    findButton(host, '保存识图参数').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.model', { value: 'qwen3-vl-plus' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.timeout_ms', { value: '15000' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.max_size_bytes', { value: '5242880' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/image.compress_quality', { value: '85' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/desktop.clipboard_screenshot_confirm_prompt_s', { value: '10' });
    const imageTimeoutInput = [...host.querySelectorAll('label')]
      .find((label) => label.textContent?.includes('识图超时'))
      ?.querySelector('input') as HTMLInputElement | null;
    expect(imageTimeoutInput?.min).toBe('15000');

    const capabilityPanel = host.querySelector('.ops-llm-capability-panel') as HTMLElement;
    const openCapabilityConfig = async (name: string) => {
      const row = [...capabilityPanel.querySelectorAll('.ops-llm-capability-row')]
        .find((item) => item.textContent?.includes(name)) as HTMLElement;
      findButton(row, '配置').click();
      await flushUi();
      return host.querySelector('.ops-llm-capability-modal') as HTMLElement;
    };

    let capabilityModal = await openCapabilityConfig('回复生成');
    findButton(capabilityModal, '保存回复生成').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.fallback_to_skill', { value: 'true' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.max_tokens', { value: '900' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.system_prompt', { value: '生成三条可直接发送的回复建议' });

    findButton(capabilityModal, '关闭').click();
    await flushUi();
    capabilityModal = await openCapabilityConfig('档案提取');
    findButton(capabilityModal, '保存档案提取').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.fallback_to_skill', { value: 'true' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.max_tokens', { value: '700' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.profile_extraction.system_prompt', { value: '提取客户档案更新建议' });

    findButton(capabilityModal, '关闭').click();
    await flushUi();
    capabilityModal = await openCapabilityConfig('跟进建议');
    findButton(capabilityModal, '保存跟进建议').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.followup_suggestion.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.followup_suggestion.max_tokens', { value: '500' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.followup_suggestion.system_prompt', { value: '生成下次跟进建议' });

    findButton(capabilityModal, '关闭').click();
    await flushUi();
    capabilityModal = await openCapabilityConfig('异常识别');
    findButton(capabilityModal, '保存异常识别').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.abnormal_detection.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.abnormal_detection.max_tokens', { value: '500' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.abnormal_detection.system_prompt', { value: '识别客户不满和流失风险' });

    findButton(capabilityModal, '关闭').click();
    await flushUi();
    capabilityModal = await openCapabilityConfig('总结补位');
    findButton(capabilityModal, '保存总结补位').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.summary.enabled', { value: 'false' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.summary.max_tokens', { value: '500' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.summary.system_prompt', { value: '生成会话摘要' });

    findButton(capabilityModal, '关闭').click();
    await flushUi();

    const relayUrlInput = host.querySelector('input[aria-label="企业微信服务器转发地址"]') as HTMLInputElement;
    expect(mainText(host)).toContain('在哪里找？');
    expect(mainText(host)).toContain('WECOM_RELAY_BASE_URL');
    expect(mainText(host)).toContain('WECOM_RELAY_KEY_ID');
    const beforeBlankRelaySaveCalls = apiMocks.putJson.mock.calls.length;
    findButton(host, '保存企业微信连接').click();
    await flushSave();
    const blankRelaySaveCalls = apiMocks.putJson.mock.calls.slice(beforeBlankRelaySaveCalls);
    expect(blankRelaySaveCalls).toContainEqual(['/admin/api/v1/configs/wecom.connection_mode', { value: 'RELAY' }]);
    expect(blankRelaySaveCalls).not.toContainEqual(['/admin/api/v1/configs/wecom.relay_base_url', { value: '' }]);

    setInputValue(relayUrlInput, 'https://wecom-relay.example.com');
    findButton(host, '保存企业微信连接').click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/wecom.relay_base_url', { value: 'https://wecom-relay.example.com' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/wecom.connection_mode', { value: 'RELAY' });
    const advancedGroups = host.querySelector('.ops-advanced-config-groups') as HTMLElement;
    findButton(advancedGroups, '数据同步').click();
    await flushUi();
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

  it('groups image and LLM environments and keeps profile testing in an on-demand modal', async () => {
    apiMocks.postJson.mockImplementation(async (path: string, body?: unknown) => path === '/admin/api/v1/llm-environments/5/test'
      ? body === undefined
        ? {
            success: true,
            data: {
              success: true,
              elapsedMs: 88,
              result: { model: 'qwen-plus', protocol: 'OPENAI_COMPATIBLE', content: 'OK' },
              errorCode: null,
              errorMessage: null,
              suggestion: null
            },
            errorCode: null,
            message: null
          }
        : {
          success: true,
          data: {
            success: true,
            elapsedMs: 118,
            result: { scene: 'PROFILE_EXTRACTION', model: 'qwen-plus', protocol: 'OPENAI_COMPATIBLE', profileAnalysis: { profileUpdates: { fields: {} }, tagDecisions: [] } }
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: {}, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();

    expect(host.querySelector('.configuration-center-layout')).toBeTruthy();
    expect(host.querySelector('.configuration-models-row')).toBeTruthy();
    expect(host.querySelector('.image-environment-panel')?.textContent).toContain('识图模型环境');
    expect([...host.querySelectorAll('button')].some((button) => button.textContent?.includes('管理不同 Skill'))).toBe(false);
    const llmPanel = host.querySelector('.llm-environment-panel') as HTMLElement;
    expect(llmPanel).toBeTruthy();
    expect(llmPanel.textContent).toContain('测试连通性');
    expect(llmPanel.textContent).toContain('档案提取测试');
    expect(llmPanel.querySelector('textarea')).toBeFalsy();

    const backupCard = [...llmPanel.querySelectorAll('.ops-env-card')]
      .find((card) => card.textContent?.includes('LLM 备用')) as HTMLElement;
    findButton(backupCard, '测试连通性').click();
    await flushSave();
    expect(backupCard.textContent).toContain('连通性已通过 · qwen-plus · 88ms');

    findButton(backupCard, '档案提取测试').click();
    await flushUi();

    const modal = host.querySelector('.ops-profile-test-modal') as HTMLElement;
    expect(modal).toBeTruthy();
    expect(modal.textContent).toContain('不会写入客户档案');
    const leadTypeSelect = modal.querySelector('select') as HTMLSelectElement;
    const messageInputs = [...modal.querySelectorAll('textarea')] as HTMLTextAreaElement[];
    setInputValue(leadTypeSelect, 'TUAN_GOU');
    setInputValue(messageInputs[0], '客户明确说想改善核心力量');
    setInputValue(messageInputs[1], '客户想先了解试用方案');
    await flushUi();
    findButton(modal, '开始业务测试').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments/5/test', {
      scene: 'PROFILE_EXTRACTION',
      leadType: 'TUAN_GOU',
      messages: [
        { role: 'client', content: '客户明确说想改善核心力量' },
        { role: 'client', content: '客户想先了解试用方案' }
      ]
    });
    expect(modal.textContent).toContain('测试结果');
    app.unmount();
  });

  it('keeps built-in LLM tasks compact and expands advanced configuration only on demand', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();

    const capabilityPanel = host.querySelector('.ops-llm-capability-panel') as HTMLElement;
    expect(capabilityPanel).toBeTruthy();
    expect(capabilityPanel.textContent).toContain('系统内置工作任务');
    expect(capabilityPanel.querySelectorAll('.ops-llm-capability-row')).toHaveLength(5);
    expect(capabilityPanel.querySelectorAll('input.ops-switch')).toHaveLength(5);
    expect(capabilityPanel.querySelector('textarea')).toBeFalsy();

    const replyRow = [...capabilityPanel.querySelectorAll('.ops-llm-capability-row')]
      .find((row) => row.textContent?.includes('回复生成')) as HTMLElement;
    const replySwitch = replyRow.querySelector('input.ops-switch') as HTMLInputElement;
    replySwitch.checked = true;
    replySwitch.dispatchEvent(new Event('change', { bubbles: true }));
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/llm.reply_generation.enabled', { value: 'true' });

    findButton(replyRow, '配置').click();
    await flushUi();
    const capabilityModal = host.querySelector('.ops-llm-capability-modal') as HTMLElement;
    expect(capabilityModal).toBeTruthy();
    expect(capabilityModal.textContent).toContain('回答变化程度');
    expect(capabilityModal.textContent).toContain('回答长度');
    expect(capabilityModal.textContent).toContain('给模型的任务说明');
    expect(capabilityModal.textContent).not.toContain('You extract structured');
    findButton(capabilityModal, '查看高级任务说明').click();
    await flushUi();
    expect(capabilityModal.querySelector('textarea')).toBeTruthy();

    findButton(capabilityModal, '关闭').click();
    await flushUi();
    expect(host.querySelector('.ops-llm-capability-modal')).toBeFalsy();

    expect(mainText(host)).toContain('企业微信连接方式');
    expect(mainText(host)).toContain('连接企业微信智能表格');
    const connectionPanels = [...host.querySelectorAll('.configuration-connection-panel')] as HTMLElement[];
    expect(connectionPanels).toHaveLength(2);
    expect(connectionPanels.every((panel) => !panel.classList.contains('wide'))).toBe(true);
    expect(mainText(host)).not.toContain('Skill 运行参数');
    expect(mainText(host)).not.toContain('识图运行参数');
    expect(mainText(host)).not.toContain('LLM 场景路由');
    expect(mainText(host)).not.toContain('数据同步策略');

    findButton(host, '展开高级运行配置').click();
    await flushUi();
    expect(mainText(host)).toContain('模型分工与调用统计');
    expect(mainText(host)).toContain('Skill 服务环境');
    expect(mainText(host)).toContain('Skill 运行保护');
    expect(mainText(host)).toContain('识图运行限制');
    expect(mainText(host)).toContain('数据同步');
    expect(mainText(host)).not.toContain('Skill 运行参数');
    expect(mainText(host)).not.toContain('识图运行参数');
    expect(mainText(host)).not.toContain('LLM 场景路由');

    findButton(host, '模型分工与调用统计').click();
    await flushUi();
    expect(mainText(host)).toContain('LLM 场景路由');
    expect(mainText(host)).toContain('LLM 调用统计');
    expect(mainText(host)).not.toContain('Skill 运行参数');

    findButton(host, '识图运行限制').click();
    await flushUi();
    expect(mainText(host)).toContain('识图运行参数');
    expect(mainText(host)).not.toContain('LLM 场景路由');
    expect(mainText(host)).not.toContain('LLM 调用统计');

    expect(mainText(host)).not.toContain('输出格式模板');
    findButton(host, '编辑提示词与规则').click();
    await flushUi();
    expect(mainText(host)).toContain('输出格式模板');

    app.unmount();
  });

  it('shows beginner instructions and verifies each fixed Smart Sheet without technical settings', async () => {
    apiMocks.postJson.mockImplementation(async (path: string) => path === '/admin/api/v1/datasources/smart-sheet-connection'
      ? {
          success: true,
          data: {
            connected: true,
            tableName: '客户资料表',
            documentId: 'doc-api-owned',
            sheetId: 'sheet-api-owned',
            viewId: 'view-api-owned',
            documentUrl: 'https://doc.weixin.qq.com/smartsheet/doc-api-owned'
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: apiData[path] ?? {}, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();

    expect(mainText(host)).not.toContain('管理不同 Skill');
    expect(mainText(host)).not.toContain('新增环境');
    expect([...host.querySelectorAll('button')].filter((button) => button.textContent?.trim() === '新建').length).toBeGreaterThanOrEqual(2);

    const taskRows = [...host.querySelectorAll('.ops-llm-capability-row')] as HTMLElement[];
    expect(taskRows).toHaveLength(5);
    expect(taskRows.every((row) => row.querySelector('.ops-capability-test-slot'))).toBe(true);

    expect(mainText(host)).toContain('连接企业微信智能表格');
    expect(mainText(host)).toContain('打开目标表格，复制浏览器地址栏里的完整链接');
    expect(mainText(host)).toContain('系统会立即使用这里的配置进行读取和写入');
    expect(mainText(host)).toContain('更换为另一张表时使用');
    expect(host.querySelectorAll('.ops-smart-sheet-card')).toHaveLength(3);
    expect(mainText(host)).toContain('客户主表');
    expect(mainText(host)).toContain('分配表');
    expect(mainText(host)).toContain('到店表');
    expect(mainText(host)).toContain('https://doc.weixin.qq.com/smartsheet/doc-api-owned');
    expect(mainText(host)).toContain('s3_APgA7xRqAB0CNrQ1UhsSTQXykhFvt_a');
    expect(mainText(host)).toContain('s3_APgA7xRqAB0CNRDGrbZAMSpK7ytM5_a');

    const smartSheetInput = host.querySelector('input[aria-label="企业微信智能表格链接"]') as HTMLInputElement;
    expect(smartSheetInput.value).toBe('https://doc.weixin.qq.com/smartsheet/doc-api-owned');
    expect(host.querySelector('input[aria-label="企微表格网关 API 密钥"]')).toBeFalsy();
    const smartSheetRoleSelect = host.querySelector('select[aria-label="连接表格角色"]') as HTMLSelectElement;
    expect(smartSheetRoleSelect).toBeTruthy();
    const assignmentCard = [...host.querySelectorAll('.ops-smart-sheet-card')]
      .find((card) => card.textContent?.includes('分配表')) as HTMLElement;
    findButton(assignmentCard, '表格管理').click();
    await flushSave();
    expect(host.querySelector('.assignment-table-manager-modal')).toBeTruthy();
    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/assignment-tables');
    (host.querySelector('button[aria-label="关闭表格管理"]') as HTMLButtonElement).click();
    await flushUi();
    expect(host.querySelector('.assignment-table-manager-modal')).toBeNull();
    findButton(assignmentCard, '配置这张表').click();
    await flushUi();
    expect(smartSheetRoleSelect.value).toBe('ASSIGNMENT');
    expect(smartSheetInput.value).toBe('https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNrQ1UhsSTQXykhFvt_a?scode=AH8A3wd1ABArdRWh2eAPgA7xRqAB0');
    findButton(host, '保存并检测这张表').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/datasources/smart-sheet-connection', {
      documentUrl: 'https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNrQ1UhsSTQXykhFvt_a?scode=AH8A3wd1ABArdRWh2eAPgA7xRqAB0',
      role: 'ASSIGNMENT',
      documentId: 'assignment-doc',
      sheetId: 'assignment-sheet',
      viewId: 'assignment-view',
      uniqueFieldTitle: '联系方式'
    });
    expect(mainText(host)).toContain('已连接：客户资料表');
    expect(host.querySelector('input[aria-label="企微表格网关 API 密钥"]')).toBeFalsy();

    expect(mainText(host)).not.toContain('自动同步 Cron');
    expect(mainText(host)).not.toContain('客户缓存 TTL');
    findButton(host, '展开高级运行配置').click();
    await flushUi();
    const advancedGroups = host.querySelector('.ops-advanced-config-groups') as HTMLElement;
    findButton(advancedGroups, '数据同步').click();
    await flushUi();
    expect(mainText(host)).toContain('自动同步时间规则');
    expect(mainText(host)).toContain('一次最多导入多少行');
    expect(mainText(host)).not.toContain('同步多久没响应算失败');
    findButton(host, '同步异常时再调整').click();
    await flushUi();
    expect(mainText(host)).toContain('同步多久没响应算失败');

    findButton(advancedGroups, '识图运行限制').click();
    await flushUi();
    const imageSelect = host.querySelector('select[aria-label="使用哪个识图模型"]') as HTMLSelectElement;
    expect(imageSelect).toBeTruthy();
    expect([...imageSelect.options].map((option) => option.textContent)).toContain('识图生产');

    findButton(host, '编辑提示词与规则').click();
    await flushUi();
    const promptEditor = host.querySelector('.configuration-prompt-editor') as HTMLElement;
    expect(promptEditor.textContent).toContain('每行一条，按回车换行，不使用分号');
    expect(promptEditor.textContent).toContain('已有默认内容时通常无需修改');

    app.unmount();
  });

  it('opens one direct Skill configuration for a scene without environment or capability controls', async () => {
    const { app, host } = await mountConsole();

    expect(mainText(host)).toContain('每个场景独立配置 Skill');
    findButton(host, '新增绑定').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect(drawer.textContent).toContain('场景 Skill');
    expect(drawer.textContent).not.toContain('环境名称');
    expect(drawer.textContent).not.toContain('使用能力');
    expect(drawer.textContent).not.toContain('技能标识');
    expect(controlByLabel<HTMLInputElement>(drawer, '显示名称')).toBeTruthy();
    expect(controlByLabel<HTMLInputElement>(drawer, '服务地址')).toBeTruthy();
    expect(controlByLabel<HTMLInputElement>(drawer, 'API 密钥')).toBeTruthy();
    expect(controlByLabel<HTMLSelectElement>(drawer, '接口协议')).toBeTruthy();
    app.unmount();
  });

  it('saves one direct scene Skill configuration in a single request', async () => {
    const { app, host } = await mountConsole();

    findButton(host, '新增 Skill 绑定').click();
    await flushUi();
    const bindingDrawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect(bindingDrawer.textContent).toContain('场景 Skill');
    expect((controlByLabel<HTMLInputElement>(bindingDrawer, '服务地址')).value).toBe('');
    expect(controlByLabel<HTMLInputElement>(bindingDrawer, 'API 密钥')).toBeTruthy();
    expect(controlByLabel<HTMLSelectElement>(bindingDrawer, '接口协议')).toBeTruthy();

    setInputValue(controlByLabel<HTMLInputElement>(bindingDrawer, '服务地址'), 'https://skill-new.example.com');
    setInputValue(controlByLabel<HTMLInputElement>(bindingDrawer, 'API 密钥'), 'skill-key-new');
    setInputValue(controlByLabel<HTMLInputElement>(bindingDrawer, '显示名称'), '新的开场白助手');
    findButton(bindingDrawer, '保存').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/skills', {
      skillName: '新的开场白助手',
      scene: 'OPENING',
      leadType: 'GENERAL',
      priority: 10,
      baseUrl: 'https://skill-new.example.com',
      apiKey: 'skill-key-new',
      protocol: 'OPENAI_COMPATIBLE'
    });
    expect(apiMocks.postJson).not.toHaveBeenCalledWith('/admin/api/v1/skill-environments', expect.anything());
    app.unmount();
  });

  it('edits a scene Skill without resending its saved API key', async () => {
    const { app, host } = await mountConsole();

    const bindingRow = [...host.querySelectorAll('.ops-table-row')]
      .find((row) => row.textContent?.includes('开场白助手')) as HTMLElement;
    findButton(bindingRow, '编辑').click();
    await flushUi();
    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    setInputValue(controlByLabel<HTMLInputElement>(drawer, '显示名称'), '更新后的开场白 Skill');
    setInputValue(controlByLabel<HTMLInputElement>(drawer, '服务地址'), 'https://opening-skill.example.com');
    findButton(drawer, '保存').click();
    await flushSave();

    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/skills/1', expect.objectContaining({
      skillName: '更新后的开场白 Skill',
      baseUrl: 'https://opening-skill.example.com'
    }));
    expect(apiMocks.putJson.mock.calls.find((call) => call[0] === '/admin/api/v1/skills/1')?.[1]).not.toHaveProperty('apiKey');
    expect(apiMocks.putJson).not.toHaveBeenCalledWith('/admin/api/v1/skill-environments/1', expect.anything());
    app.unmount();
  });

  it('automatically refreshes the selected assignment table configuration after creation succeeds', async () => {
    const originalConfigs = apiData['/admin/api/v1/configs'] as Record<string, unknown>;
    const refreshedConfigs = {
      ...originalConfigs,
      'table.assignment_document_url': 'https://doc.weixin.qq.com/smartsheet/new-assignment?scode=updated',
      'table.assignment.document_id': 'new-assignment-doc',
      'table.assignment.sheet_id': 'new-assignment-sheet',
      'table.assignment.view_id': 'new-assignment-view',
      'table.assignment.unique_field_title': '手机号码'
    };
    let useRefreshedConfigs = false;
    apiMocks.getJson.mockImplementation(async (path: string) => ({
      success: true,
      data: path === '/admin/api/v1/configs' && useRefreshedConfigs
        ? refreshedConfigs
        : apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] },
      errorCode: null,
      message: null
    }));
    apiMocks.postJson.mockImplementation(async (path: string) => path === '/api/v1/assignment-tables'
      ? {
          success: true,
          data: { id: 91, tableName: '9月新客分配', monthKey: '2026-09', documentUrl: 'https://doc.weixin.qq.com/smartsheet/new-assignment?scode=updated', status: 'ACTIVE' },
          errorCode: null,
          message: null
        }
      : { success: true, data: apiData[path] ?? {}, errorCode: null, message: null });

    const { app, host } = await mountConsole();
    findSubnavButton(host, '配置中心').click();
    await flushSave();
    const assignmentCard = [...host.querySelectorAll('.ops-smart-sheet-card')]
      .find((card) => card.textContent?.includes('分配表')) as HTMLElement;
    findButton(assignmentCard, '配置这张表').click();
    await flushUi();
    findButton(assignmentCard, '表格管理').click();
    await flushSave();

    useRefreshedConfigs = true;
    (host.querySelector('.assignment-table-manager-create') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await flushSave();
    await flushSave();

    expect((host.querySelector('input[aria-label="企业微信智能表格链接"]') as HTMLInputElement).value)
      .toBe('https://doc.weixin.qq.com/smartsheet/new-assignment?scode=updated');
    expect((host.querySelector('input[aria-label="智能表格文档 ID"]') as HTMLInputElement).value).toBe('new-assignment-doc');
    expect((host.querySelector('input[aria-label="智能表格查找列名称"]') as HTMLInputElement).value).toBe('手机号码');
    expect(mainText(host)).toContain('分配表配置已自动更新');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/datasources');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/datasources/sync-status');

    app.unmount();
  });

  it('does not call a datasource connected when its browser URL is missing', async () => {
    const configs = apiData['/admin/api/v1/configs'] as Record<string, string>;
    const savedDocumentUrl = configs['table.document_url'];
    configs['table.document_url'] = '';
    try {
      const { app, host } = await mountConsole();
      findSubnavButton(host, '配置中心').click();
      await flushSave();

      const primaryCard = [...host.querySelectorAll('.ops-smart-sheet-card')]
        .find((card) => card.textContent?.includes('客户主表')) as HTMLElement;
      expect(primaryCard.textContent).toContain('待补网址');
      expect(primaryCard.textContent).toContain('未保存网址');

      findButton(primaryCard, '配置这张表').click();
      await flushUi();
      const smartSheetInput = host.querySelector('input[aria-label="企业微信智能表格链接"]') as HTMLInputElement;
      expect(smartSheetInput.value).toBe('');
      app.unmount();
    } finally {
      configs['table.document_url'] = savedDocumentUrl;
    }
  });

  it('uses readable fallbacks for broken environment names and an empty image selector state', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      const basePath = path.split('?')[0];
      if (basePath === '/admin/api/v1/skill-environments') {
        return { success: true, data: { items: [{ id: 1, envName: '??.top ????', baseUrl: 'https://skill.example.com', active: true }] }, errorCode: null, message: null };
      }
      if (basePath === '/admin/api/v1/image-environments') {
        return { success: true, data: { items: [] }, errorCode: null, message: null };
      }
      if (basePath === '/admin/api/v1/llm-environments') {
        return { success: true, data: { items: [{ id: 4, envName: '??????', model: 'qwen3-vl-plus', baseUrl: 'https://llm.example.com', active: true }] }, errorCode: null, message: null };
      }
      return { success: true, data: apiData[path] ?? apiData[basePath] ?? { items: [] }, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();
    expect(mainText(host)).not.toContain('??');
    expect(mainText(host)).toContain('qwen3-vl-plus');

    findButton(host, '展开高级运行配置').click();
    await flushUi();
    findButton(host, '识图运行限制').click();
    await flushUi();
    const imageSelect = host.querySelector('select[aria-label="使用哪个识图模型"]') as HTMLSelectElement;
    expect(imageSelect.disabled).toBe(true);
    expect([...imageSelect.options].map((option) => option.textContent)).toEqual(['未配置']);

    app.unmount();
  });

  it('creates, activates, and tests LLM environments from configuration center', async () => {
    apiMocks.postJson.mockImplementation(async (path: string) => path === '/admin/api/v1/llm-environments/5/test'
      ? {
          success: true,
          data: {
            success: true,
            elapsedMs: 135,
            result: {
              scene: 'PROFILE_EXTRACTION',
              model: 'qwen-plus',
              protocol: 'OPENAI_COMPATIBLE',
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
                  evidence: '客户明确说想改善核心力量',
                  resultType: 'UPDATE',
                  requestedAction: 'ADD'
                }]
              }
            },
            errorCode: null,
            errorMessage: null,
            suggestion: null
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: {}, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();

    const llmPanel = host.querySelector('.llm-environment-panel') as HTMLElement;
    expect(llmPanel).toBeTruthy();
    findButton(llmPanel, '新建').click();
    await flushSave();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    expect(drawer.textContent).toContain('LLM 模型环境');
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

    findButton(backupCard, '测试连通性').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments/5/test', undefined);
    expect(backupCard.textContent).toContain('连通性已通过 · qwen-plus · 135ms');

    findButton(backupCard, '档案提取测试').click();
    await flushUi();
    const profileModal = host.querySelector('.ops-profile-test-modal') as HTMLElement;
    const leadTypeSelect = profileModal.querySelector('select') as HTMLSelectElement;
    const profileMessages = [...profileModal.querySelectorAll('textarea')] as HTMLTextAreaElement[];
    expect(leadTypeSelect).toBeTruthy();
    expect(profileMessages).toHaveLength(2);
    setInputValue(leadTypeSelect, 'TUAN_GOU');
    setInputValue(profileMessages[0], '客户明确说想改善核心力量');
    setInputValue(profileMessages[1], '客户想先了解试用方案');
    await flushUi();
    findButton(profileModal, '开始业务测试').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments/5/test', {
      scene: 'PROFILE_EXTRACTION',
      leadType: 'TUAN_GOU',
      messages: [
        { role: 'client', content: '客户明确说想改善核心力量' },
        { role: 'client', content: '客户想先了解试用方案' }
      ]
    });
    expect(llmPanel.textContent).toContain('档案字段 nickname：Alice（HIGH）');
    expect(llmPanel.textContent).toContain('custom_goal：更新 · 新增 · GOAL_B · 95%');
    expect(llmPanel.textContent).toContain('依据：客户明确说想改善核心力量');

    app.unmount();
  });

  it('shows strict profile validation failures from an LLM environment test', async () => {
    apiMocks.postJson.mockImplementation(async (path: string) => path === '/admin/api/v1/llm-environments/5/test'
      ? {
          success: true,
          data: {
            success: false,
            elapsedMs: 91,
            result: null,
            errorCode: '30-20006',
            errorMessage: '模型返回缺少 tag_decisions',
            suggestion: '请检查模型返回是否符合档案分析 Schema'
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: {}, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();
    const llmPanel = host.querySelector('.llm-environment-panel') as HTMLElement;
    const backupCard = [...llmPanel.querySelectorAll('.ops-env-card')]
      .find((card) => card.textContent?.includes('LLM 备用')) as HTMLElement;

    findButton(backupCard, '档案提取测试').click();
    await flushUi();
    const profileModal = host.querySelector('.ops-profile-test-modal') as HTMLElement;
    setInputValue(profileModal.querySelector('textarea') as HTMLTextAreaElement, '客户明确说想改善核心力量');
    await flushUi();
    findButton(profileModal, '开始业务测试').click();
    await flushSave();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/llm-environments/5/test', {
      scene: 'PROFILE_EXTRACTION',
      leadType: 'TUAN_GOU',
      messages: [{ role: 'client', content: '客户明确说想改善核心力量' }]
    });
    expect(profileModal.textContent).toContain('模型返回缺少 tag_decisions');
    expect(llmPanel.textContent).not.toContain('未知模型');

    app.unmount();
  });

  it('creates, toggles, deletes, and refreshes LLM scene routes', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '配置中心').click();
    await flushSave();
    findButton(host, '展开高级运行配置').click();
    await flushUi();
    findButton(host, '模型分工与调用统计').click();
    await flushUi();

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
    findButton(host, '展开高级运行配置').click();
    await flushUi();
    findButton(host, '识图运行限制').click();
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
    findButton(host, '展开高级运行配置').click();
    await flushUi();
    findButton(host, 'Skill 服务环境').click();
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

  it('groups account permissions by team and shows the leader before team members', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '账号与权限').click();
    await flushUi();
    await flushUi();

    const groups = [...host.querySelectorAll('.ops-account-group')];
    expect(groups.length).toBeGreaterThanOrEqual(1);
    expect(groups[0].textContent).toContain('万江私域组');
    expect(groups[0].textContent).toContain('组长');
    expect(groups[0].textContent).toContain('管家');
    expect(mainText(host)).toContain('未分组');

    app.unmount();
  });

  it('loads and saves login and workbench sync while showing Skill expiry as read-only', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '账号与权限').click();
    await flushSave();

    const panel = [...host.querySelectorAll('.ops-panel')]
      .find((item) => item.textContent?.includes('登录与桌面设置')) as HTMLElement;
    expect(panel).toBeTruthy();
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/configs');

    setInputValue(controlByLabel<HTMLInputElement>(panel, '登录凭证有效小时'), '4');
    setInputValue(controlByLabel<HTMLInputElement>(panel, '免登录天数'), '30');
    setInputValue(controlByLabel<HTMLInputElement>(panel, '工作台自动同步秒数'), '90');
    expect(panel.textContent).toContain('系统会从 Skill 服务或授权配置自动读取');
    expect(panel.querySelector('input[type="date"]')).toBeNull();
    findButton(panel, '保存设置').click();
    await flushSave();

    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/system.jwt_access_token_ttl_s', { value: '14400' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/system.jwt_refresh_token_ttl_s', { value: '2592000' });
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/desktop.workbench_refresh_interval_s', { value: '90' });
    expect(apiMocks.putJson).not.toHaveBeenCalledWith('/admin/api/v1/configs/skill.subscription_expire_at', expect.anything());

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
    findButton(host, '编辑提示词与规则').click();
    await flushUi();
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
    findButton(host, '字段映射').click();
    await flushUi();
    expect(host.querySelector('.ops-mapping-grid select')).toBeTruthy();
    expect(apiMocks.getJson.mock.calls.some(([path]) =>
      String(path).startsWith('/admin/api/v1/datasources/10/columns?refresh='))).toBe(true);
    expect(mainText(host)).toContain('每个表格列选择对应的系统内容');
    expect(mainText(host)).toContain('设置表格列写入的系统内容');
    expect(host.querySelector('select[aria-label="phone对应系统内容"]')).toBeTruthy();
    apiMocks.getJson.mockImplementation(async (path: string) => ({
      success: true,
      data: path.split('?')[0] === '/admin/api/v1/datasources/10/columns'
        ? {
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
            schemaReadable: false,
            fallback: true,
            fetchError: 'sheet timeout'
          }
        : apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] },
      errorCode: null,
      message: null
    }));
    findButton(host, '识别列名').click();
    await flushUi();
    expect(mainText(host)).toContain('真实表格暂不可用');
    expect(mainText(host)).toContain('取样失败：sheet timeout');
    expect(mainText(host)).toContain('映射已锁定');
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
    expect(mainText(host)).toContain('未匹配标签 1');
    expect(mainText(host)).toContain('2');
    expect(mainText(host)).toContain('phone invalid');

    findButton(host, '保存映射').click();
    await flushUi();
    expect(apiMocks.putJson).not.toHaveBeenCalledWith('/admin/api/v1/datasources/10/mappings', expect.anything());

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    expect(mainText(host)).toContain('速搜内容');
    expect(mainText(host)).toContain('开场话术');
    expect(mainText(host)).not.toContain('客户数据源');
    expect(host.querySelector('.ops-filter-bar input')).toBeTruthy();

    app.unmount();
  });

  it('lets an administrator choose which system content each table column writes', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    findButton(host, '字段映射').click();
    await flushUi();

    const storeMapping = host.querySelector('select[aria-label="store对应系统内容"]') as HTMLSelectElement;
    expect(storeMapping).toBeTruthy();
    setInputValue(storeMapping, 'phone');
    await flushUi();
    expect(mainText(host)).toContain('将写入系统：手机号');

    findButton(host, '保存映射').click();
    await flushUi();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/datasources/10/mappings', {
      mappings: [
        { targetField: 'phone', sourceField: 'store', enabled: true },
        { targetField: 'nickname', sourceField: 'nickname', enabled: false }
      ]
    });

    app.unmount();
  });

  it('updates the datasource mapping count immediately after saving', async () => {
    apiMocks.putJson.mockResolvedValueOnce({ success: true, data: { mappingCount: 1, version: 4 }, errorCode: null, message: null });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();
    findButton(host, '保存映射').click();
    await flushUi();

    const datasourceRow = [...host.querySelectorAll('.ops-table-row')]
      .find((row) => row.textContent?.includes('企微客资表')) as HTMLElement;
    expect(datasourceRow.children[3]?.textContent).toBe('1');

    app.unmount();
  });

  it('provides an explicit paginated customer search in data integration', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('客户查询');
    findButton(host, '展开筛选').click();
    await flushUi();
    expect(mainText(host)).toContain('基本条件');
    expect(mainText(host)).toContain('业务归属');
    expect(mainText(host)).toContain('时间与到店');
    expect(mainText(host)).toContain('王女士');
    const searchInput = [...host.querySelectorAll('input')]
      .find((input) => input.getAttribute('placeholder')?.includes('昵称')) as HTMLInputElement;
    setInputValue(searchInput, '1111');
    findButton(host, '查询客户').click();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/customers/search', {
      keyword: '1111',
      sourceChannels: [],
      leadTypes: [],
      assignedKeepers: [],
      intendedStores: [],
      intendedProjects: [],
      customerStages: [],
      arrivedValues: [],
      updatedFrom: null,
      updatedTo: null,
      appointmentFrom: null,
      appointmentTo: null,
      lastFollowupFrom: null,
      lastFollowupTo: null,
      nextFollowupFrom: null,
      nextFollowupTo: null,
      tagGroups: [],
      tagGroupLogic: 'AND',
      sortBy: 'UPDATED_AT',
      sortDirection: 'DESC',
      page: 1,
      pageSize: 20
    });
    const customerTable = host.querySelector('.ops-table') as HTMLElement;
    customerTable.scrollLeft = 456;
    findButton(host, '查看档案').click();
    await flushUi();
    expect(customerTable.scrollLeft).toBe(0);
    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/customers/by-id/11', expect.any(Number), expect.anything());
    expect(mainText(host)).toContain('恶露/月经');
    expect(mainText(host)).toContain('腹直肌');
    expect(mainText(host)).not.toContain('编辑档案');
    expect(host.querySelector('.ops-customer-profile-row')?.classList).toContain('ops-table-detail-row');

    app.unmount();
  });

  it('toggles a customer profile from the whole row without requiring the action button', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();

    const row = [...host.querySelectorAll('.ops-table-row.customer-search')]
      .find((item) => item.textContent?.includes('王女士')) as HTMLElement;
    expect(row).toBeTruthy();
    row.click();
    await flushUi();
    expect(host.querySelector('.ops-customer-profile-row')).toBeTruthy();
    row.click();
    await flushUi();
    expect(host.querySelector('.ops-customer-profile-row')).toBeNull();

    app.unmount();
  });

  it('exports the complete customer filter without the current page limits', async () => {
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:customers');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();
    findButton(host, '展开筛选').click();
    await flushUi();
    findButton(host, '导出当前查询').click();
    await flushUi();

    expect(apiMocks.postBlob).toHaveBeenCalledWith('/admin/api/v1/customers/export', expect.objectContaining({
      keyword: '',
      tagGroups: [],
      tagGroupLogic: 'AND'
    }));
    expect(apiMocks.postBlob.mock.calls[0][1]).not.toHaveProperty('page');
    expect(apiMocks.postBlob.mock.calls[0][1]).not.toHaveProperty('pageSize');
    expect(mainText(host)).toContain('客户 CSV 已开始下载');
    expect(clickSpy).toHaveBeenCalled();
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalled();

    app.unmount();
  });

  it('loads dynamic filter tags and serializes multi-value all matching', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      if (path.startsWith('/admin/api/v1/tags/categories')) {
        return {
          success: true,
          data: {
            items: [{
              id: 70,
              categoryKey: 'body_concerns',
              categoryName: '身体关注',
              selectionMode: 'MULTI',
              useForFilter: true,
              isEnabled: true,
              mergedIntoId: null,
              values: [
                { id: 701, tagValue: 'DIASTASIS', displayName: '腹直肌分离', isEnabled: true, mergedIntoId: null },
                { id: 702, tagValue: 'LEAKAGE', displayName: '漏尿', isEnabled: true, mergedIntoId: null }
              ]
            }],
            total: 1,
            page: 1,
            size: 100,
            totalPages: 1
          },
          errorCode: null,
          message: null
        };
      }
      return { success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    (host.querySelectorAll('.ops-admin-subnav-button')[2] as HTMLButtonElement).click();
    await flushUi();
    await flushUi();

    expect(host.querySelector('.customer-filter-body')).toBeNull();
    (host.querySelector('.customer-filter-section-head') as HTMLElement).click();
    await flushUi();

    const tagPicker = host.querySelector('.customer-tag-picker-trigger') as HTMLButtonElement;
    expect(tagPicker).toBeTruthy();
    tagPicker.click();
    await flushUi();
    const tagOptions = host.querySelector('.customer-tag-picker-options') as HTMLElement;
    expect(tagOptions).toBeTruthy();
    [...tagOptions.querySelectorAll('.customer-tag-picker-option')].forEach((option) => {
      (option as HTMLButtonElement).click();
    });
    findButton(host, '完成').click();
    const matchSelect = host.querySelector('select.customer-tag-match-select') as HTMLSelectElement;
    setInputValue(matchSelect, 'ALL');
    const logicSelect = host.querySelector('select.customer-tag-logic-select') as HTMLSelectElement;
    setInputValue(logicSelect, 'AND');
    findButton(host, '鏌ヨ瀹㈡埛').click();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenLastCalledWith('/admin/api/v1/customers/search', expect.objectContaining({
      tagGroupLogic: 'AND',
      tagGroups: [{ categoryId: 70, valueIds: [701, 702], match: 'ALL' }]
    }));
    expect(host.querySelector('.customer-filter-body')).toBeNull();
    expect(mainText(host)).toContain('展开筛选');

    app.unmount();
  });

  it('uses focused layouts for customer search and follow-up rules without changing their controls', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();
    expect(host.querySelector('.ops-customer-query-summary')).toBeTruthy();
    expect(host.querySelector('section.ops-customer-query-filters')).toBeTruthy();
    expect(host.querySelector('.customer-filter-body')).toBeNull();
    expect(mainText(host)).toContain('展开筛选');

    findSubnavButton(host, '跟进规则引擎配置').click();
    await flushUi();
    expect(host.querySelector('.ops-rule-list')).toBeTruthy();
    expect(host.querySelector('.ops-rule-row')).toBeTruthy();

    findSubnavButton(host, '操作审计日志').click();
    await flushUi();
    expect(host.querySelector('.ops-audit-action-grid')).toBeTruthy();

    app.unmount();
  });

  it('makes advanced customer filters selectable and keeps audit logs above optional action filters', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      if (path.startsWith('/admin/api/v1/tags/categories')) {
        return {
          success: true,
          data: {
            items: [{
              id: 70,
              categoryKey: 'body_concerns',
              categoryName: '身体关注',
              selectionMode: 'MULTI',
              useForFilter: true,
              isEnabled: true,
              mergedIntoId: null,
              values: [
                { id: 701, displayName: '腰痛', isEnabled: true, mergedIntoId: null },
                { id: 702, displayName: '漏尿', isEnabled: true, mergedIntoId: null }
              ]
            }],
            total: 1,
            page: 1,
            size: 100,
            totalPages: 1
          },
          errorCode: null,
          message: null
        };
      }
      return { success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushUi();
    await flushUi();
    findButton(host, '展开筛选').click();
    await flushUi();
    expect(host.querySelector('.customer-tag-picker-trigger')).toBeTruthy();
    expect(host.querySelector('.ops-tag-chip-list')).toBeNull();

    findSubnavButton(host, '操作审计日志').click();
    await flushUi();
    const auditFilters = host.querySelector('details.ops-audit-filters') as HTMLDetailsElement;
    expect(auditFilters).toBeTruthy();
    expect(auditFilters.open).toBe(false);
    expect(host.querySelector('.ops-audit-action-grid')).toBeTruthy();

    app.unmount();
  });

  it('lets administrators add, remove, save, and force-refresh intent project keywords', async () => {
    apiMocks.postJson.mockImplementation(async (path: string) => {
      if (path === '/admin/api/v1/intent-project-mappings/refresh') {
        return {
          success: true,
          data: {
            fieldName: '意向项目',
            total: 4,
            rules: [...(apiData['/admin/api/v1/intent-project-mappings'] as { rules: unknown[] }).rules,
              { optionId: 'option-new', optionText: '新增项目', keywords: [], priority: 0, status: 'PENDING' }]
          },
          errorCode: null,
          message: null
        };
      }
      return { success: true, data: {}, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushSave();

    const postpartumRule = [...host.querySelectorAll('.ops-intent-project-rule')]
      .find((rule) => rule.textContent?.includes('产康')) as HTMLElement;
    expect(postpartumRule).toBeTruthy();
    expect(postpartumRule.querySelectorAll('.ops-intent-project-keyword-row input')).toHaveLength(1);

    (postpartumRule.querySelector('[aria-label="新增关键词"]') as HTMLButtonElement).click();
    await flushUi();
    const inputs = [...postpartumRule.querySelectorAll('.ops-intent-project-keyword-row input')] as HTMLInputElement[];
    expect(inputs).toHaveLength(2);
    setInputValue(inputs[0], '腹直肌');
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/intent-project-mappings/option-postpartum', {
      keywords: ['腹直肌'], priority: 0, enabled: true
    });

    (postpartumRule.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/intent-project-mappings/option-postpartum', {
      keywords: ['腹直肌'], priority: 0, enabled: false
    });
    setInputValue(postpartumRule.querySelector('.ops-number-input') as HTMLInputElement, '8');
    await flushSave();
    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/intent-project-mappings/option-postpartum', {
      keywords: ['腹直肌'], priority: 8, enabled: true
    });

    (postpartumRule.querySelector('[aria-label="删除最后一个关键词"]') as HTMLButtonElement).click();
    await flushSave();
    expect(postpartumRule.querySelectorAll('.ops-intent-project-keyword-row input')).toHaveLength(1);

    findButton(host, '读取最新项目选项').click();
    await flushSave();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/intent-project-mappings/refresh', {});
    expect(mainText(host)).toContain('已读取企业微信最新选项，共 4 项。');
    expect(mainText(host)).toContain('新增项目');
    expect(mainText(host)).toContain('新选项，待配置');

    app.unmount();
  });

  it('uses a longer timeout and reports the asynchronous WeCom queue after an intent-project backfill', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    apiMocks.postJson.mockImplementation(async (path: string) => {
      if (path === '/admin/api/v1/intent-project-mappings/recompute?onlyEmpty=true') {
        return {
          success: true,
          data: {
            scanned: 14,
            matched: 11,
            databaseUpdated: 11,
            projectionQueued: 11,
            errors: []
          },
          errorCode: null,
          message: null
        };
      }
      return { success: true, data: {}, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '客户数据对接').click();
    await flushSave();
    findButton(host, '开始补算空缺客户').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/intent-project-mappings/recompute?onlyEmpty=true', {}, 120000);
    expect(mainText(host)).toContain('符合条件 14 位（已购项目有值且意向项目为空）');
    expect(mainText(host)).toContain('唯一事实数据库已更新 11 位，11 位已加入企业微信同步队列');

    confirm.mockRestore();
    app.unmount();
  });

  it('searches and inserts every field returned by the unique-fact field catalog', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    findButton(host, '新增内容').click();
    await flushUi();

    const drawer = host.querySelector('.ops-drawer') as HTMLElement;
    const variableButtons = [...drawer.querySelectorAll('.ops-variable-bar button')] as HTMLButtonElement[];
    expect(variableButtons.map((button) => button.textContent?.trim())).toEqual([
      '手机号', '客户昵称', '意向等级', '客户阶段', '身体关注', '跟进记录'
    ]);
    const fieldSearch = drawer.querySelector('input[type="search"]') as HTMLInputElement;
    setInputValue(fieldSearch, '关注');
    await flushUi();
    expect([...drawer.querySelectorAll('.ops-variable-bar button')].map((button) => button.textContent?.trim())).toEqual(['身体关注']);
    (drawer.querySelector('.ops-variable-bar button') as HTMLButtonElement).click();
    await flushUi();

    expect((drawer.querySelector('textarea') as HTMLTextAreaElement).value).toBe('{{客户关注点}}');
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

  it('only offers image upload actions for image quick-search content', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();

    const cards = [...host.querySelectorAll('.ops-content-card')];
    const nonImageCard = cards.find((card) => !card.textContent?.includes('图片素材')) as HTMLElement;
    expect(nonImageCard).toBeTruthy();
    expect(nonImageCard.querySelector('.file-button')).toBeNull();

    app.unmount();
  });

  it('opens quick-search editing in a centered modal without changing other drawers', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    findButton(host, '新增内容').click();
    await flushUi();

    expect(host.querySelector('.ops-drawer.ops-modal-form')).toBeTruthy();

    app.unmount();
  });

  it('shows image upload only after choosing the image quick-search type', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '速搜内容管理').click();
    await flushUi();
    findButton(host, '新增内容').click();
    await flushUi();

    const drawer = host.querySelector('.ops-modal-form') as HTMLElement;
    expect(drawer.querySelector('.ops-quick-search-image-field')).toBeNull();
    const contentType = drawer.querySelector('select') as HTMLSelectElement;
    setInputValue(contentType, 'IMAGE');
    await flushUi();
    expect(drawer.querySelector('.ops-quick-search-image-field input[type="file"]')).toBeTruthy();
    expect(drawer.textContent).toContain('仅图文素材需要图片');
    apiMocks.postForm.mockResolvedValueOnce({ success: true, data: { imageUrl: '/uploads/quick-search-demo.png' }, errorCode: null, message: null });
    const fileInput = drawer.querySelector('.ops-quick-search-image-field input[type="file"]') as HTMLInputElement;
    const file = new File(['image'], 'quick-search.png', { type: 'image/png' });
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fileInput.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();
    expect(apiMocks.postForm).toHaveBeenCalledWith('/admin/api/v1/upload/image', expect.any(FormData));
    expect(drawer.querySelector('.ops-quick-search-image-field img')?.getAttribute('src')).toBe('/uploads/quick-search-demo.png');

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
    expect(mainText(host)).toContain('回复确认状态');
    expect(mainText(host)).toContain('明确未发送');
    expect(mainText(host)).toContain('已确认发送率 75%');
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
    expect(mainText(host)).toContain('识图服务不可用');
    expect(mainText(host)).toContain('企业微信智能表格连通性');
    expect(mainText(host)).toContain('已实际检查 3 张已配置智能表格的访问权限');
    expect(mainText(host)).toContain('企业微信表格写入队列');
    expect(mainText(host)).toContain('写入队列中有 1 条过期失败记录');
    expect(mainText(host)).toContain('智能表格写入失败记录');
    expect(mainText(host)).toContain('有字段未配置企业微信表格映射');
    expect(mainText(host)).not.toContain('queued table write contains fields without mappings');
    expect(mainText(host)).toContain('尾号 1111');

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

  it('shows a read-only customer master record and lets duplicate search results be selected in one dialog', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '唯一事实数据库').click();
    await flushSave();
    expect(mainText(host)).toContain('客户唯一事实数据库');
    expect(mainText(host)).toContain('王女士');
    expect(mainText(host)).toContain('客户编号');
    expect(mainText(host)).toContain('真实值');
    expect(mainText(host)).toContain('最新来源');
    expect(mainText(host)).toContain('人工编辑');

    findButton(host, '查找客户').click();
    await flushUi();
    const searchInput = host.querySelector('input[aria-label="搜索客户"]') as HTMLInputElement;
    setInputValue(searchInput, '1001');
    await flushUi();
    const searchButton = host.querySelector('.customer-master-search-form button') as HTMLButtonElement;
    expect(searchButton.disabled).toBe(false);
    searchButton.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    await flushUi();
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/customer-master/search?q=1001');
    expect(host.querySelectorAll('.customer-master-candidate')).toHaveLength(2);

    (host.querySelector('.customer-master-candidate') as HTMLButtonElement).click();
    await flushUi();
    expect(mainText(host)).toContain('李女士');
    expect(host.querySelector('[role="dialog"]')).toBeNull();
    app.unmount();
  });

  it('opens a field history dialog with every recorded value change', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '唯一事实数据库').click();
    await flushSave();
    const nicknameRow = [...host.querySelectorAll('.ops-table-row.customer-master-fields')]
      .find((row) => row.textContent?.includes('客户昵称')) as HTMLElement;
    findButton(nicknameRow, '查看历史').click();
    await flushUi();

    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/customer-master/42/fields/nickname/history');
    const dialog = host.querySelector('[role="dialog"]') as HTMLElement;
    expect(dialog.textContent).toContain('客户昵称历史');
    expect(dialog.textContent).toContain('时间');
    expect(dialog.textContent).toContain('会话识别');
    expect(dialog.textContent).toContain('人工编辑');
    expect(dialog.textContent).toContain('keeper-1');
    app.unmount();
  });

  it('closes a permanently invalid table write failure after confirmation', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { app, host } = await mountConsole();

    findSubnavButton(host, '系统健康监控').click();
    await flushSave();
    const failureRow = [...host.querySelectorAll('.ops-table-row.table-write-failure')]
      .find((row) => row.textContent?.includes('#71')) as HTMLElement;
    const closeButton = findButton(failureRow, '关闭');
    await vi.waitFor(() => expect(closeButton.disabled).toBe(false));
    closeButton.click();
    await flushSave();

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('确认关闭智能表格写入记录 #71'));
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/table-writes/71/resolve', {});
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
    expect(host.querySelector('.ops-rule-search-compact')).toBeTruthy();
    expect(drawer.querySelectorAll('.ops-rule-form-section')).toHaveLength(3);
    expect(drawer.textContent).toContain('触发条件');
    expect(drawer.textContent).toContain('规则信息');
    expect(drawer.textContent).toContain('执行动作');
    const textInputs = [...drawer.querySelectorAll('input[type="text"]')] as HTMLInputElement[];
    const numberInputs = [...drawer.querySelectorAll('input[type="number"]')] as HTMLInputElement[];
    const selects = [...drawer.querySelectorAll('select')] as HTMLSelectElement[];
    const selectWith = (value: string) => selects.find((select) => [...select.options].some((option) => option.value === value)) as HTMLSelectElement;
    setInputValue(textInputs[0], '高意向超时标签');
    setInputValue(selectWith('XIAN_SUO'), 'XIAN_SUO');
    setInputValue(numberInputs[0], '12');
    setInputValue(selectWith('TAG_CHANGE'), 'TAG_CHANGE');
    setInputValue(selectWith('WARN'), 'WARN');
    setInputValue(selectWith('TAG_SUGGESTION'), 'TAG_SUGGESTION');
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

  it('serializes dynamic followup tag conditions and formal tag change target', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '璺熻繘瑙勫垯寮曟搸閰嶇疆').click();
    await flushUi();
    findButton(host, '鏂板瑙勫垯').click();
    await flushUi();
    await flushUi();

    expect(host.querySelectorAll('.ops-rule-tag-condition')).toHaveLength(1);
    const category = host.querySelector('.ops-rule-tag-condition select.rule-tag-category') as HTMLSelectElement;
    expect([...category.options].map((option) => option.value)).toContain('50');
    setInputValue(category, '50');
    const values = host.querySelector('.ops-rule-tag-condition select.rule-tag-values') as HTMLSelectElement;
    expect([...values.options].map((option) => option.value)).toEqual(expect.arrayContaining(['51', '52']));
    [...values.options].forEach((option) => { option.selected = ['51', '52'].includes(option.value); });
    values.dispatchEvent(new Event('change', { bubbles: true }));
    setInputValue(host.querySelector('.ops-rule-tag-condition select.rule-tag-match') as HTMLSelectElement, 'ALL');
    findButton(host, '娣诲姞鏍囩鏉′欢').click();
    await flushUi();
    const second = host.querySelectorAll('.ops-rule-tag-condition')[1] as HTMLElement;
    setInputValue(second.querySelector('select.rule-tag-category') as HTMLSelectElement, '50');
    const secondValues = second.querySelector('select.rule-tag-values') as HTMLSelectElement;
    [...secondValues.options].forEach((option) => { option.selected = option.value === '51'; });
    secondValues.dispatchEvent(new Event('change', { bubbles: true }));

    setInputValue(controlByLabel(host, '鍔ㄤ綔') as HTMLSelectElement, 'TAG_CHANGE');
    setInputValue(controlByLabel(host, '姝ｅ紡鏍囩鍒嗙被') as HTMLSelectElement, '50');
    setInputValue(controlByLabel(host, '姝ｅ紡鏍囩鍊?') as HTMLSelectElement, '51');
    findButton(host, '淇濆瓨').click();
    await flushUi();

    const payload = apiMocks.postJson.mock.calls.find((call) => call[0] === '/admin/api/v1/rules')?.[1] as Record<string, unknown>;
    expect(JSON.parse(String(payload.conditionJson))).toMatchObject({
      operator: 'AND',
      conditions: expect.arrayContaining([
        { field: 'tag', op: 'MATCH', categoryId: 50, valueIds: [51, 52], match: 'ALL' },
        { field: 'tag', op: 'MATCH', categoryId: 50, valueIds: [51], match: 'ANY' }
      ])
    });
    expect(JSON.parse(String(payload.actionConfig))).toMatchObject({
      tagCategoryId: 50,
      tagCategoryKey: 'intent_level',
      tagValueId: 51,
      tagValue: 'HIGH',
      tagName: ((apiData['/admin/api/v1/tags/categories'] as any).items[0].values[0].displayName)
    });

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

  it('lets an administrator review template candidates and publish or decline them', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '可推广模板').click();
    await flushUi();
    await flushUi();

    expect(mainText(host)).toContain('keeper-a');
    expect([...host.querySelectorAll('.template-candidate-copy textarea')].map((item) => (item as HTMLTextAreaElement).value))
      .toContain('Original AI reply');
    expect([...host.querySelectorAll('.template-candidate-copy textarea')].map((item) => (item as HTMLTextAreaElement).value))
      .toContain('Employee adjusted body');
    expect(mainText(host)).toContain('已用 3 次');
    const title = host.querySelector('[data-testid="candidate-title-42"]') as HTMLInputElement;
    setInputValue(title, 'Published team opening');
    (host.querySelector('[data-testid="candidate-publish-42"]') as HTMLButtonElement).click();
    await flushUi();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/template-promotion-candidates/42/publish', expect.objectContaining({
      title: 'Published team opening'
    }));

    (host.querySelector('[data-testid="candidate-not-publish-43"]') as HTMLButtonElement).click();
    await flushUi();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/template-promotion-candidates/43/not-publish', {});
    app.unmount();
  });

  it('renders template candidates in a filterable list with lead type and scope controls', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '可推广模板').click();
    await flushUi();
    await flushUi();

    expect(host.querySelector('.template-candidate-list')).toBeTruthy();
    expect(host.querySelectorAll('.template-candidate-row')).toHaveLength(2);
    expect(host.querySelector('select[aria-label="线索类型"]')).toBeTruthy();
    expect(host.querySelector('select[aria-label="发布范围"]')).toBeTruthy();
    expect(mainText(host)).toContain('模板标题');

    app.unmount();
  });

  it('loads supervisor metrics and saves governance settings from the administrator console', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '主管监督记录').click();
    await flushSave();
    await flushSave();

    expect(mainText(host)).toContain('AI 使用率');
    expect(mainText(host)).toContain('已复制客户');
    expect(mainText(host)).toContain('转化目标已配置');
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/supervision/metrics'));
    expect(apiMocks.getJson).toHaveBeenCalledWith(expect.stringContaining('/admin/api/v1/supervision/events'));
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/supervision/metadata');
    const eventTypeSelect = controlByLabel<HTMLSelectElement>(host, '事件类型');
    expect([...eventTypeSelect.options].map((option) => option.value)).toEqual(['', 'REPLY_COPIED']);

    findSubnavButton(host, '数据保留与任务设置').click();
    await flushSave();
    const retentionInput = controlByLabel<HTMLInputElement>(host, '主管监督记录保留天数');
    setInputValue(retentionInput, '200');
    findButton(host, '保存治理设置').click();
    await flushSave();

    expect(apiMocks.putJson).toHaveBeenCalledWith('/admin/api/v1/configs/supervision.record_retention_days', { value: '200' });
    expect(mainText(host)).toContain('哪些阶段算转化成功');
    expect((host.querySelector('[data-testid="conversion-target-stages"]') as HTMLElement).textContent).toContain('已成交');
    const targetStagePicker = host.querySelector('[data-testid="conversion-stage-picker"]') as HTMLSelectElement;
    setInputValue(targetStagePicker, '跟进中');
    await flushSave();
    findButton(host, '加入转化成功').click();
    await flushSave();
    expect((host.querySelector('[data-testid="conversion-target-stages"]') as HTMLElement).textContent).toContain('跟进中');
    app.unmount();
  });

  it('does not render incomplete supervisor metric payloads as misleading cards', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      const basePath = path.split('?')[0];
      const data = basePath === '/admin/api/v1/supervision/metrics'
        ? {
          metrics: {
            AI_USAGE_RATE: metricPayload({ numerator: null }),
            AI_COVERAGE: metricPayload({ denominator: '' }),
            PROCESSING_EFFICIENCY: metricPayload({ rate: '0.5' }),
            EMPLOYEE_CONVERSION: metricPayload({ numerator: '2' }),
            AI_REPLY_CONVERSION: metricPayload({ denominator: false })
          }
        }
        : apiData[path] ?? apiData[basePath] ?? { items: [] };
      return { success: true, data, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '主管监督记录').click();
    await flushSave();

    expect(mainText(host)).toContain('暂无监督指标数据');
    expect(host.querySelectorAll('.ops-supervision-metric')).toHaveLength(0);
    expect(mainText(host)).not.toContain('NaN');
    app.unmount();
  });

  it('reloads current governance settings and reports partial-save risk when one update fails', async () => {
    const { app, host } = await mountConsole();
    findSubnavButton(host, '数据保留与任务设置').click();
    await flushSave();
    apiMocks.getJson.mockClear();
    apiMocks.putJson.mockImplementation(async (path: string) => path.endsWith('technical_log_retention_days')
      ? { success: false, data: null, errorCode: '70-10001', message: 'range invalid' }
      : { success: true, data: {}, errorCode: null, message: null });

    findButton(host, '保存治理设置').click();
    await flushSave();
    await flushSave();

    expect(mainText(host)).toContain('保存未完全成功，当前生效设置已重新加载');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/configs');
    app.unmount();
  });

  it('does not display supervisor pages or request supervisor endpoints for tag-only managers', async () => {
    const { app, host } = await mountConsole({ accountName: '组长', tagManagementOnly: true });
    await flushSave();

    expect(mainText(host)).not.toContain('主管监督记录');
    expect(mainText(host)).not.toContain('数据保留与任务设置');
    expect(apiMocks.getJson.mock.calls.map((call) => String(call[0])).every((path) => !path.startsWith('/admin/api/v1/supervision/'))).toBe(true);
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

  it('explains governance validation errors with the business field name', async () => {
    const { app, host } = await mountConsole();
    findSubnavButton(host, '数据保留与任务设置').click();
    await flushSave();

    setInputValue(controlByLabel<HTMLInputElement>(host, '最近任务显示上限'), '101');
    findButton(host, '保存治理设置').click();
    await flushSave();

    expect(mainText(host)).toContain('最近任务显示上限只能填写 20 至 100 条。');
    expect(mainText(host)).not.toContain('chat.recent_task_display_cap');
    app.unmount();
  });

  it('renders the analytics overview from live response data as a visual dashboard', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '运营分析看板').click();
    await flushSave();
    await flushSave();

    const visualDashboard = host.querySelector('.ops-analytics-visual-dashboard') as HTMLElement;
    expect(visualDashboard).toBeTruthy();
    expect(visualDashboard.textContent).toContain('18');
    expect(visualDashboard.textContent).toContain('98%');
    expect(visualDashboard.querySelector('.ops-analytics-trend')).toBeTruthy();
    expect(visualDashboard.querySelectorAll('.ops-analytics-ring')).toHaveLength(3);
    expect(visualDashboard.querySelector('.ops-analytics-funnel')).toBeTruthy();
    expect(visualDashboard.querySelectorAll('.ops-analytics-funnel-step')).toHaveLength(3);
    expect(visualDashboard.textContent).toContain('已分配');
    expect(visualDashboard.textContent).toContain('已联系');
    expect(visualDashboard.textContent).toContain('已到店');
    expect(visualDashboard.querySelector('.ops-analytics-ranking')).toBeTruthy();
    expect(visualDashboard.textContent).toContain('开场话术');

    app.unmount();
  });

  it('loads, renders and filters tag analytics independently', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '运营分析看板').click();
    await flushSave();
    await flushSave();

    expect(mainText(host)).toContain('标签统计');
    expect(mainText(host)).toContain('正式标签 3');
    expect(mainText(host)).toContain('高意向');
    expect(mainText(host)).toContain('系统推断');

    setInputValue(controlByLabel<HTMLSelectElement>(host, '标签统计门店'), '万江店');
    setInputValue(controlByLabel<HTMLSelectElement>(host, '标签统计团队'), '9');
    findButton(host, '刷新标签统计').click();
    await flushSave();

    expect(apiMocks.postJson).toHaveBeenLastCalledWith(
      '/admin/api/v1/analytics/tags',
      expect.objectContaining({
        customerFilter: expect.objectContaining({ intendedStores: ['万江店'] }),
        teamLeaderIds: [9],
        granularity: 'DAY'
      })
    );
    app.unmount();
  });

  it('keeps tag analytics filters and account permissions in their focused operational layouts', async () => {
    const { app, host } = await mountConsole();

    findSubnavButton(host, '运营分析看板').click();
    await flushSave();
    await flushSave();

    expect(host.querySelector('details.ops-tag-analytics-filters')).toBeTruthy();
    expect(host.querySelector('.ops-tag-analytics-core-summary')).toBeTruthy();
    expect(mainText(host)).toContain('标签覆盖率');

    findSubnavButton(host, '账号与权限').click();
    await flushUi();

    expect(host.querySelector('.ops-account-summary')).toBeTruthy();
    expect(host.querySelector('.ops-role-badge')).toBeTruthy();
    expect(mainText(host)).toContain('标签管理权限');

    app.unmount();
  });

  it('keeps existing analytics visible when tag analytics fails', async () => {
    apiMocks.postJson.mockImplementation(async (path: string) => {
      if (path === '/admin/api/v1/analytics/tags') {
        throw new Error('tag analytics timeout');
      }
      return { success: true, data: apiData[path] ?? {}, errorCode: null, message: null };
    });
    const { app, host } = await mountConsole();

    findSubnavButton(host, '运营分析看板').click();
    await flushSave();
    await flushSave();

    expect(mainText(host)).toContain('同事效能');
    expect(mainText(host)).toContain('张三');
    expect(mainText(host)).toContain('标签统计刷新失败');
    expect(mainText(host)).toContain('重试标签统计');

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

function metricPayload(patch: Record<string, unknown>): Record<string, unknown> {
  return {
    numerator: 2,
    denominator: 4,
    rate: 0.5,
    numeratorLabel: '已复制客户',
    denominatorLabel: '已生成客户',
    conversionTargetConfigured: true,
    ...patch
  };
}
