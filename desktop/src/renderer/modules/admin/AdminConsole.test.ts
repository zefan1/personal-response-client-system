import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminConsole from './AdminConsole.vue';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postForm: vi.fn(),
  postJson: vi.fn(),
  putJson: vi.fn(),
  deleteJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
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

const apiData: Record<string, unknown> = {
  '/admin/api/v1/skills': {
    items: [
      { id: 1, scene: 'OPENING', leadType: 'TUAN_GOU', skillId: 'skill_opening', skillName: '开场白助手', priority: 90, enabled: true }
    ]
  },
  '/admin/api/v1/skills/available': {
    items: [{ skillId: 'skill_opening', skillName: '开场白助手' }, { skillId: 'skill_reply', skillName: '主动回复助手' }]
  },
  '/admin/api/v1/analytics/skill-calls': { summary: { calls: 8, successRate: '99%' } },
  '/admin/api/v1/skill-environments': {
    items: [{ id: 1, envName: '生产环境', baseUrl: 'https://skill.example.com', apiKeyLast4: '1234', active: true }]
  },
  '/admin/api/v1/image-environments': {
    items: [{ id: 2, envName: '识图生产', baseUrl: 'https://image.example.com', lastTestOk: true, lastTestAt: '2026-07-03T08:00:00Z', isActive: true }]
  },
  '/admin/api/v1/configs': {
    'skill.system_prompt_format': '按客户阶段输出',
    'skill.system_prompt_red_lines': '["不得承诺疗效"]',
    'match.tag_removal_rules': '["L1-"]',
    'skill.fallback_reply': '稍后回复'
  },
  '/admin/api/v1/datasources': {
    items: [{ id: 10, name: '企微客资表', sheetId: 'sheet-a', sourceTable: 'leads', enabled: true }]
  },
  '/admin/api/v1/customer-fields': {
    items: [{ key: 'phone', label: '手机号' }, { key: 'nickname', label: '客户昵称' }]
  },
  '/admin/api/v1/datasources/sync-status': {
    items: [{ datasourceId: 10, syncStatus: 'SUCCESS', mappingCount: 2 }]
  },
  '/admin/api/v1/datasources/import-logs': { items: [] },
  '/admin/api/v1/quick-search/items': {
    items: [{ id: 20, contentType: 'TEMPLATE', leadType: 'GENERAL', title: '开场话术', shortcutCode: 'hi', content: '您好', enabled: true }]
  },
  '/admin/api/v1/accounts': {
    items: [
      { id: 30, displayName: '管理员', phone: '18800000000', role: 'ADMIN', isEnabled: true },
      { id: 31, displayName: '组长', phone: '18800000001', role: 'LEADER', isEnabled: true }
    ]
  },
  '/admin/api/v1/rules': {
    items: [{ id: 40, name: '24 小时未跟进提醒', actionType: 'ALERT', priority: 90, enabled: true, isBuiltin: true, conditionPreview: '待确认客户超过 24 小时未跟进' }]
  },
  '/admin/api/v1/tags/categories': {
    items: [{ id: 50, categoryName: '意向度', boundField: 'intentLevel', values: [{ id: 51, tagValue: 'HIGH', displayName: '高意向' }] }]
  },
  '/admin/api/v1/analytics/overview': { summary: { calls: 18, successRate: '98%' } },
  '/admin/api/v1/analytics/funnels': { summary: { newLead: 12, converted: 3 } },
  '/admin/api/v1/analytics/staff': { summary: { activeKeepers: 6 } },
  '/admin/api/v1/analytics/sources': { summary: { wecom: 12 } },
  '/admin/api/v1/analytics/stages': { summary: { pending: 5 } },
  '/admin/api/v1/analytics/health': { summary: { stable: 1 } },
  '/admin/api/v1/analytics/lifecycle': { summary: { retained: 2 } },
  '/admin/api/v1/analytics/risks': { summary: { stale: 1 } },
  '/admin/api/v1/analytics/content-ranking': { summary: { top: '开场话术' } },
  '/admin/api/v1/versions': {
    items: [{ id: 60, version: '1.0.1', platform: 'WINDOWS', updateStrategy: 'OPTIONAL', status: 'DRAFT' }]
  },
  '/admin/api/v1/notices': {
    items: [{ id: 70, title: '系统维护', level: 'INFO', status: '有效', content: '今晚维护' }]
  },
  '/admin/api/v1/audit-logs': {
    items: [{ id: 80, action: 'CREATE_NOTICE', operator: 'admin', detail: '创建公告', createdAt: '2026-07-03T09:00:00Z' }]
  },
  '/admin/api/v1/audit-logs/actions': { items: [{ action: 'CREATE_NOTICE' }] },
  '/admin/api/v1/health': {
    status: 'OK',
    components: {
      db: { status: 'UP', duration: 'PT1M' },
      redis: { status: 'UP', duration: 'PT1M' },
      skill: { status: 'UP', duration: 'PT1M' },
      imageRecognition: { status: 'UP', duration: 'PT1M' },
      wecomTable: { status: 'UP', duration: 'PT1M' }
    },
    recentAlerts: []
  }
};

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountConsole(): Promise<MountedConsole> {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AdminConsole, { accountName: 'admin' });
  app.mount(host);
  await flushUi();
  return { app, host };
}

function findButton(host: HTMLElement, text: string): HTMLButtonElement {
  const button = [...host.querySelectorAll('button')].find((item) => item.textContent?.includes(text)) as HTMLButtonElement | undefined;
  expect(button).toBeTruthy();
  return button as HTMLButtonElement;
}

function setInputValue(element: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement, value: string) {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('AdminConsole product surface', () => {
  beforeEach(() => {
    apiMocks.getJson.mockImplementation(async (path: string) => ({ success: true, data: apiData[path] ?? apiData[path.split('?')[0]] ?? { items: [] }, errorCode: null, message: null }));
    apiMocks.postForm.mockResolvedValue({ success: true, data: { totalRows: 1, created: 1, updated: 0, skipped: 0, errors: [] }, errorCode: null, message: null });
    apiMocks.postJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.putJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.deleteJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    apiMocks.getJson.mockReset();
    apiMocks.postForm.mockReset();
    apiMocks.postJson.mockReset();
    apiMocks.putJson.mockReset();
    apiMocks.deleteJson.mockReset();
    vi.restoreAllMocks();
  });

  it('renders the operations admin as four business module groups instead of an API console', async () => {
    const { app, host } = await mountConsole();

    expect(host.querySelector('.ops-admin-shell')).toBeTruthy();
    expect([...host.querySelectorAll('.ops-admin-nav span')].map((item) => item.textContent)).toEqual([
      'AI 与 Skill 配置',
      '数据源与内容',
      '组织、规则与标签',
      '分析与系统运营'
    ]);
    expect(host.textContent).toContain('Skill 场景绑定');
    expect(host.textContent).toContain('开场白助手');
    expect(host.textContent).toContain('Prompt 与规则');
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
    setInputValue(selects[0], 'ACTIVE_REPLY');
    setInputValue(selects[1], 'XIAN_SUO');
    setInputValue(selects[2], 'skill_reply');
    setInputValue(textInputs[0], '主动回复助手');
    setInputValue(priorityInput, '77');

    findButton(drawer!, '保存').click();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/skills', {
      scene: 'ACTIVE_REPLY',
      leadType: 'XIAN_SUO',
      skillId: 'skill_reply',
      skillName: '主动回复助手',
      priority: 77
    });

    app.unmount();
  });

  it('covers data/content workflows with filters, mapping, CSV preview, and card actions', async () => {
    const { app, host } = await mountConsole();

    findButton(host, '数据源与内容').click();
    await flushUi();

    expect(host.textContent).toContain('客户数据源');
    expect(host.textContent).toContain('字段映射');
    expect(host.textContent).toContain('CSV 导入');
    expect(host.textContent).toContain('速搜内容');
    expect(host.querySelector('.ops-filter-bar input')).toBeTruthy();
    expect(host.querySelector('.ops-mapping-grid input')).toBeTruthy();
    expect(host.textContent).toContain('开场话术');

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

    app.unmount();
  });

  it('covers organization, rules, tags, analytics, releases, notices, audit, and health views', async () => {
    const { app, host } = await mountConsole();

    findButton(host, '组织、规则与标签').click();
    await flushUi();
    expect(host.textContent).toContain('账号与权限');
    expect(host.textContent).toContain('跟进规则');
    expect(host.textContent).toContain('标签与分层');
    expect(host.textContent).toContain('24 小时未跟进提醒');
    expect(host.querySelector('.ops-filter-bar input')).toBeTruthy();

    findButton(host, '分析与系统运营').click();
    await flushUi();
    expect(host.textContent).toContain('运营分析看板');
    expect(host.textContent).toContain('桌面版本');
    expect(host.textContent).toContain('系统公告');
    expect(host.textContent).toContain('审计日志');
    expect(host.textContent).toContain('系统健康');
    expect(host.textContent).toContain('CREATE_NOTICE');

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
