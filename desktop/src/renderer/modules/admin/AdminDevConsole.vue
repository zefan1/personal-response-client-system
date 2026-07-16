<template>
  <section class="admin-console">
    <aside class="admin-sidebar">
      <div>
        <h2>开发调试台</h2>
        <p>{{ sessionLabel }}</p>
      </div>
      <button
        v-for="section in sections"
        :key="section.key"
        class="admin-nav-button"
        :class="{ active: selectedKey === section.key }"
        type="button"
        @click="selectSection(section.key)"
      >
        <span>{{ section.title }}</span>
        <small>{{ section.reads.length }} 个读取接口</small>
      </button>
    </aside>

    <main class="admin-main">
      <header class="admin-toolbar">
        <div>
          <h1>{{ selectedSection.title }}</h1>
          <p>{{ selectedSection.description }}</p>
        </div>
        <div class="admin-toolbar-actions">
          <button class="secondary" type="button" @click="$emit('switch-admin')">正式后台</button>
          <button class="secondary" type="button" @click="$emit('logout')">退出</button>
        </div>
      </header>

      <p v-if="notice" class="admin-message" :class="{ error: noticeKind === 'error' }">{{ notice }}</p>

      <section class="admin-band">
        <div class="admin-section-head">
          <h2>数据读取</h2>
          <button class="primary" type="button" :disabled="loading" @click="loadSection(selectedSection)">刷新全部</button>
        </div>
        <div class="admin-read-grid">
          <article v-for="read in selectedSection.reads" :key="read.name" class="admin-read-panel">
            <div class="admin-card-head">
              <div>
                <h3>{{ read.name }}</h3>
                <code>{{ read.method }} {{ read.path }}</code>
              </div>
              <button class="small secondary" type="button" :disabled="loading" @click="runRead(read)">刷新</button>
            </div>
            <pre>{{ formatJson(readResults[read.path]) }}</pre>
          </article>
        </div>
      </section>

      <section class="admin-band">
        <div class="admin-section-head">
          <h2>操作入口</h2>
          <span class="status-pill">{{ selectedSection.actions.length }} 个动作</span>
        </div>
        <div class="admin-action-grid">
          <article v-for="action in selectedSection.actions" :key="action.name" class="admin-action-panel">
            <div class="admin-card-head">
              <div>
                <h3>{{ action.name }}</h3>
                <code>{{ action.method }} {{ action.pathTemplate }}</code>
              </div>
              <button class="primary small" type="button" :disabled="loading" @click="runAction(action)">执行</button>
            </div>
            <label v-if="action.needsId">
              目标 ID
              <input v-model="actionState[action.name].id" inputmode="numeric" placeholder="先从上方列表复制 id" />
            </label>
            <div v-if="editableFields(action).length" class="admin-field-grid">
              <label v-for="field in editableFields(action)" :key="field.key">
                {{ field.label }}
                <select v-if="field.kind === 'enum'" :value="String(field.value ?? '')" @change="updateActionField(action, field.key, ($event.target as HTMLSelectElement).value)">
                  <option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
                </select>
                <input
                  v-else-if="field.kind === 'boolean'"
                  type="checkbox"
                  :checked="Boolean(field.value)"
                  @change="updateActionField(action, field.key, ($event.target as HTMLInputElement).checked)"
                />
                <input
                  v-else-if="field.kind === 'number'"
                  type="number"
                  :value="field.value ?? ''"
                  @input="updateActionField(action, field.key, numberFieldValue(($event.target as HTMLInputElement).value))"
                />
                <input
                  v-else
                  :value="String(field.value ?? '')"
                  @input="updateActionField(action, field.key, ($event.target as HTMLInputElement).value)"
                />
              </label>
            </div>
            <label>
              请求体 JSON
              <textarea v-model="actionState[action.name].body" spellcheck="false" />
            </label>
            <pre>{{ formatJson(actionResults[action.name]) }}</pre>
          </article>
        </div>
      </section>
    </main>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { deleteJson, getJson, postJson, putJson, type ApiResponse } from '../../shared/apiClient';

type ReadEndpoint = {
  name: string;
  method: 'GET';
  path: string;
};

type ActionEndpoint = {
  name: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  pathTemplate: string;
  needsId?: boolean;
  body?: unknown;
};

type AdminSection = {
  key: string;
  title: string;
  description: string;
  reads: ReadEndpoint[];
  actions: ActionEndpoint[];
};

type ActionState = Record<string, { id: string; body: string }>;
type EditableField = {
  key: string;
  label: string;
  kind: 'text' | 'number' | 'boolean' | 'enum';
  value: unknown;
  options?: string[];
};

const props = defineProps<{
  accountName: string;
}>();

defineEmits<{
  logout: [];
  'switch-admin': [];
}>();

const nowSuffix = () => Date.now().toString().slice(-6);
const sections: AdminSection[] = [
  {
    key: 'health',
    title: '健康与系统配置',
    description: '查看系统健康、运行配置和可编辑参数。',
    reads: [
      { name: '健康检查', method: 'GET', path: '/admin/api/v1/health' },
      { name: '配置列表', method: 'GET', path: '/admin/api/v1/configs' },
      { name: '配置中心', method: 'GET', path: '/admin/api/v1/configs?prefix=skill.' }
    ],
    actions: [
      {
        name: '更新配置',
        method: 'PUT',
        pathTemplate: '/admin/api/v1/configs/{id}',
        needsId: true,
        body: { value: '300', description: 'manual acceptance update' }
      }
    ]
  },
  {
    key: 'skills',
    title: '技能场景绑定',
    description: '管理技能、场景、线索类型、优先级和启停测试。',
    reads: [
      { name: '绑定列表', method: 'GET', path: '/admin/api/v1/skills' },
      { name: '可用技能', method: 'GET', path: '/admin/api/v1/skills/available' },
      { name: '调用统计', method: 'GET', path: '/admin/api/v1/analytics/skill-calls' }
    ],
    actions: [
      {
        name: '创建绑定',
        method: 'POST',
        pathTemplate: '/admin/api/v1/skills',
        body: { skillId: `manual-${nowSuffix()}`, skillName: '人工验收技能', scene: 'OPENING', leadType: 'PENDING', priority: 90 }
      },
      {
        name: '更新绑定',
        method: 'PUT',
        pathTemplate: '/admin/api/v1/skills/{id}',
        needsId: true,
        body: { skillId: `manual-${nowSuffix()}`, skillName: '人工验收技能更新', scene: 'OPENING', leadType: 'PENDING', priority: 80 }
      },
      { name: '启停绑定', method: 'PUT', pathTemplate: '/admin/api/v1/skills/{id}/toggle', needsId: true, body: { enabled: false } },
      { name: '删除绑定', method: 'DELETE', pathTemplate: '/admin/api/v1/skills/{id}', needsId: true }
    ]
  },
  {
    key: 'ai',
    title: 'AI 与外部环境',
    description: '管理 Skill API、LLM 思考环境、图片识别环境和提示词版本。',
    reads: [
      { name: 'Skill 环境', method: 'GET', path: '/admin/api/v1/skill-environments' },
      { name: '图片环境', method: 'GET', path: '/admin/api/v1/image-environments' },
      { name: 'LLM 环境', method: 'GET', path: '/admin/api/v1/llm-environments' },
      { name: 'Format Prompt 版本', method: 'GET', path: '/admin/api/v1/skill-prompt/format/versions' }
    ],
    actions: [
      {
        name: '创建 Skill 环境',
        method: 'POST',
        pathTemplate: '/admin/api/v1/skill-environments',
        body: { envName: `manual-skill-${nowSuffix()}`, baseUrl: 'https://example.com/skill', apiKey: 'replace-with-real-key' }
      },
      { name: '激活 Skill 环境', method: 'PUT', pathTemplate: '/admin/api/v1/skill-environments/{id}/activate', needsId: true },
      {
        name: '创建图片环境',
        method: 'POST',
        pathTemplate: '/admin/api/v1/image-environments',
        body: { envName: `manual-image-${nowSuffix()}`, baseUrl: 'https://example.com/image', apiKey: 'replace-with-real-key' }
      },
      { name: '测试图片环境', method: 'POST', pathTemplate: '/admin/api/v1/image-environments/{id}/test', needsId: true },
      {
        name: '创建 LLM 环境',
        method: 'POST',
        pathTemplate: '/admin/api/v1/llm-environments',
        body: { envName: `manual-llm-${nowSuffix()}`, baseUrl: 'https://example.com', apiKey: 'replace-with-real-key', model: 'gpt-4.1-mini', protocol: 'OPENAI_COMPATIBLE', timeoutMs: 10000, temperature: 0.2, maxTokens: 1024 }
      },
      { name: '激活 LLM 环境', method: 'PUT', pathTemplate: '/admin/api/v1/llm-environments/{id}/activate', needsId: true },
      { name: '测试 LLM 环境', method: 'POST', pathTemplate: '/admin/api/v1/llm-environments/{id}/test', needsId: true }
    ]
  },
  {
    key: 'datasources',
    title: '数据源与字段映射',
    description: '管理 WeCom 表数据源、字段映射、版本、同步和导入日志。',
    reads: [
      { name: '数据源列表', method: 'GET', path: '/admin/api/v1/datasources' },
      { name: '客户字段', method: 'GET', path: '/admin/api/v1/customer-fields' },
      { name: '同步状态', method: 'GET', path: '/admin/api/v1/datasources/sync-status' },
      { name: '导入日志', method: 'GET', path: '/admin/api/v1/datasources/import-logs' }
    ],
    actions: [
      {
        name: '创建数据源',
        method: 'POST',
        pathTemplate: '/admin/api/v1/datasources',
        body: { name: `人工验收数据源${nowSuffix()}`, sheetId: `sheet-${nowSuffix()}`, sourceTable: `manual_${nowSuffix()}`, description: 'manual acceptance' }
      },
      {
        name: '保存映射',
        method: 'PUT',
        pathTemplate: '/admin/api/v1/datasources/{id}/mappings',
        needsId: true,
        body: { mappings: [{ sourceField: 'phone', targetField: 'phone', enabled: true }, { sourceField: 'nickname', targetField: 'nickname', enabled: true }] }
      },
      { name: '映射对比', method: 'GET', pathTemplate: '/admin/api/v1/datasources/{id}/mappings/compare', needsId: true },
      { name: '启停数据源', method: 'PUT', pathTemplate: '/admin/api/v1/datasources/{id}/toggle', needsId: true, body: { enabled: false } },
      { name: '删除数据源', method: 'DELETE', pathTemplate: '/admin/api/v1/datasources/{id}', needsId: true }
    ]
  },
  {
    key: 'quick-search',
    title: '快捷搜索内容',
    description: '管理话术、素材、快捷码和桌面可见内容。',
    reads: [
      { name: '管理列表', method: 'GET', path: '/admin/api/v1/quick-search/items' },
      { name: '桌面列表', method: 'GET', path: '/api/v1/quick-search/items' }
    ],
    actions: [
      {
        name: '创建内容',
        method: 'POST',
        pathTemplate: '/admin/api/v1/quick-search/items',
        body: { contentType: 'TEMPLATE', leadType: 'GENERAL', title: `人工验收话术${nowSuffix()}`, shortcutCode: `M${nowSuffix()}`, content: '人工验收内容', imageUrl: null, sortOrder: 99, enabled: true }
      },
      {
        name: '更新内容',
        method: 'PUT',
        pathTemplate: '/admin/api/v1/quick-search/items/{id}',
        needsId: true,
        body: { title: '人工验收话术更新', shortcutCode: `U${nowSuffix()}`, content: '人工验收内容更新', sortOrder: 88, enabled: true }
      },
      { name: '启停内容', method: 'PUT', pathTemplate: '/admin/api/v1/quick-search/items/{id}/toggle', needsId: true },
      { name: '删除内容', method: 'DELETE', pathTemplate: '/admin/api/v1/quick-search/items/{id}', needsId: true }
    ]
  },
  {
    key: 'accounts',
    title: '账号权限',
    description: '管理管理员、组长、跟进人账号和密码重置。',
    reads: [{ name: '账号列表', method: 'GET', path: '/admin/api/v1/accounts' }],
    actions: [
      {
        name: '创建账号',
        method: 'POST',
        pathTemplate: '/admin/api/v1/accounts',
        body: { phone: `139${Date.now().toString().slice(-8)}`, password: 'pass1234', displayName: '人工验收组长', role: 'LEADER', leaderId: null }
      },
      { name: '启停账号', method: 'PUT', pathTemplate: '/admin/api/v1/accounts/{id}/toggle', needsId: true, body: { isEnabled: false } },
      { name: '重置密码', method: 'PUT', pathTemplate: '/admin/api/v1/accounts/{id}/reset-password', needsId: true, body: { newPassword: 'pass5678' } },
      { name: '删除账号', method: 'DELETE', pathTemplate: '/admin/api/v1/accounts/{id}', needsId: true }
    ]
  },
  {
    key: 'rules-tags',
    title: '跟进规则与标签',
    description: '维护跟进提醒规则、标签分类和值。',
    reads: [
      { name: '跟进规则', method: 'GET', path: '/admin/api/v1/rules' },
      { name: '标签分类', method: 'GET', path: '/admin/api/v1/tags/categories' }
    ],
    actions: [
      {
        name: '创建规则',
        method: 'POST',
        pathTemplate: '/admin/api/v1/rules',
        body: { name: `人工验收规则${nowSuffix()}`, conditionJson: '{"conditions":[{"field":"leadType","operator":"EQ","value":"PENDING"}]}', actionType: 'ALERT', actionConfig: '{"level":"WARN"}', priority: 90, enabled: true }
      },
      { name: '启停规则', method: 'PUT', pathTemplate: '/admin/api/v1/rules/{id}/toggle', needsId: true, body: { enabled: false } },
      { name: '删除规则', method: 'DELETE', pathTemplate: '/admin/api/v1/rules/{id}', needsId: true },
      {
        name: '创建标签值',
        method: 'POST',
        pathTemplate: '/admin/api/v1/tags/values',
        body: { categoryId: null, displayName: '人工验收标签', isEnabled: true, sortOrder: 99 }
      }
    ]
  },
  {
    key: 'analytics',
    title: '数据分析',
    description: '查看漏斗、员工、来源、阶段、健康度和内容排行。',
    reads: [
      { name: '总览', method: 'GET', path: '/admin/api/v1/analytics/overview' },
      { name: '漏斗', method: 'GET', path: '/admin/api/v1/analytics/funnels' },
      { name: '员工', method: 'GET', path: '/admin/api/v1/analytics/staff' },
      { name: '来源', method: 'GET', path: '/admin/api/v1/analytics/sources' },
      { name: '阶段', method: 'GET', path: '/admin/api/v1/analytics/stages' },
      { name: '健康', method: 'GET', path: '/admin/api/v1/analytics/health' },
      { name: '生命周期', method: 'GET', path: '/admin/api/v1/analytics/lifecycle' },
      { name: '风险', method: 'GET', path: '/admin/api/v1/analytics/risks' },
      { name: '内容排行', method: 'GET', path: '/admin/api/v1/analytics/content-ranking' }
    ],
    actions: [
      {
        name: '标签统计',
        method: 'POST',
        pathTemplate: '/admin/api/v1/analytics/tags',
        body: {
          customerFilter: {
            sourceChannels: [],
            leadTypes: [],
            assignedKeepers: [],
            intendedStores: [],
            intendedProjects: [],
            customerStages: [],
            updatedFrom: null,
            updatedTo: null,
            tagGroups: [],
            tagGroupLogic: 'AND'
          },
          teamLeaderIds: [],
          tagFrom: null,
          tagTo: null,
          granularity: 'DAY'
        }
      }
    ]
  },
  {
    key: 'ops',
    title: '公告、版本、审计',
    description: '管理系统公告、桌面版本、审计日志和导出。',
    reads: [
      { name: '公告列表', method: 'GET', path: '/admin/api/v1/notices' },
      { name: '桌面版本', method: 'GET', path: '/admin/api/v1/versions' },
      { name: '审计日志', method: 'GET', path: '/admin/api/v1/audit-logs' },
      { name: '审计动作', method: 'GET', path: '/admin/api/v1/audit-logs/actions' }
    ],
    actions: [
      {
        name: '创建公告',
        method: 'POST',
        pathTemplate: '/admin/api/v1/notices',
        body: { title: `人工验收公告${nowSuffix()}`, content: '人工验收公告内容', level: 'INFO', publishType: 'IMMEDIATE', publishAt: null, expireDays: 1 }
      },
      {
        name: '创建定时公告',
        method: 'POST',
        pathTemplate: '/admin/api/v1/notices',
        body: { title: `人工验收定时公告${nowSuffix()}`, content: '人工验收定时公告内容', level: 'WARN', publishType: 'SCHEDULED', publishAt: new Date(Date.now() + 3600000).toISOString(), expireDays: 2 }
      },
      { name: '停止公告', method: 'PUT', pathTemplate: '/admin/api/v1/notices/{id}/stop', needsId: true },
      { name: '删除公告', method: 'DELETE', pathTemplate: '/admin/api/v1/notices/{id}', needsId: true },
      {
        name: '创建版本',
        method: 'POST',
        pathTemplate: '/admin/api/v1/versions',
        body: { version: `9.9.${nowSuffix()}`, platform: 'WINDOWS', downloadUrl: 'https://example.com/installer.exe', changelog: 'manual acceptance', updateStrategy: 'OPTIONAL', gradualPercent: null, fileSize: 12345 }
      },
      {
        name: '创建 Mac 版本',
        method: 'POST',
        pathTemplate: '/admin/api/v1/versions',
        body: { version: `9.8.${nowSuffix()}`, platform: 'MAC', downloadUrl: 'https://example.com/installer.dmg', changelog: 'manual acceptance mac', updateStrategy: 'OPTIONAL', gradualPercent: null, fileSize: 12345 }
      },
      { name: '发布版本', method: 'PUT', pathTemplate: '/admin/api/v1/versions/{id}/publish', needsId: true },
      { name: '撤销版本', method: 'PUT', pathTemplate: '/admin/api/v1/versions/{id}/revoke', needsId: true, body: { reason: 'manual acceptance cleanup', alternativeVersion: null } },
      { name: '导出审计', method: 'POST', pathTemplate: '/admin/api/v1/audit-logs/export', body: { action: null, operator: null, startTime: null, endTime: null } }
    ]
  }
];

const selectedKey = ref(sections[0].key);
const loading = ref(false);
const notice = ref('');
const noticeKind = ref<'info' | 'error'>('info');
const readResults = reactive<Record<string, unknown>>({});
const actionResults = reactive<Record<string, unknown>>({});
const actionState = reactive<ActionState>({});
const availableTagCategoryId = ref<number | null>(null);
const availableTagCategoryIds = ref<number[]>([]);

const TAG_CATEGORIES_PATH = '/admin/api/v1/tags/categories';
const TAG_CATEGORY_PAGE_SIZE = 100;
let tagCategoryRequestSequence = 0;
let pendingOperations = 0;

for (const section of sections) {
  for (const action of section.actions) {
    actionState[action.name] = {
      id: '',
      body: action.body === undefined ? '' : JSON.stringify(action.body, null, 2)
    };
  }
}

const selectedSection = computed(() => sections.find((section) => section.key === selectedKey.value) ?? sections[0]);
const sessionLabel = computed(() => (props.accountName ? `当前账号：${props.accountName}` : '当前账号已登录'));

onMounted(() => {
  void loadSection(selectedSection.value);
});

function selectSection(key: string) {
  selectedKey.value = key;
  void loadSection(selectedSection.value);
}

async function loadSection(section: AdminSection) {
  for (const read of section.reads) {
    await runRead(read);
  }
}

async function runRead(read: ReadEndpoint) {
  const requestSequence = read.path === TAG_CATEGORIES_PATH ? ++tagCategoryRequestSequence : null;
  await runWithNotice(async () => {
    if (read.path === TAG_CATEGORIES_PATH) {
      clearAvailableTagCategories();
    }
    const response = read.path === TAG_CATEGORIES_PATH
      ? await loadAvailableTagCategories(requestSequence as number)
      : await getJson<unknown>(read.path);
    if (response == null) return;
    readResults[read.path] = response;
    if (read.path === TAG_CATEGORIES_PATH) {
      updateAvailableTagCategory(response);
    }
  }, `${read.name} 已刷新`);
}

async function loadAvailableTagCategories(requestSequence: number): Promise<ApiResponse<unknown> | null> {
  let page = 1;
  let totalPages = 1;
  let lastResponse: ApiResponse<unknown> | null = null;
  const categories: unknown[] = [];

  while (page <= totalPages) {
    const query = new URLSearchParams({
      enabled: 'true',
      merged: 'false',
      page: String(page),
      size: String(TAG_CATEGORY_PAGE_SIZE)
    });
    const response = await getJson<unknown>(`${TAG_CATEGORIES_PATH}?${query.toString()}`);
    if (requestSequence !== tagCategoryRequestSequence) return null;
    if (!response.success) {
      throw new Error(response.message || '标签分类读取失败');
    }
    lastResponse = response;
    const data = asRecord(response.data);
    const pageItems = extractCategoryItems(data);
    const pageTotalPages = readTotalPages(data);
    if (pageItems == null || pageTotalPages == null) {
      throw new Error('标签分类分页数据不完整');
    }
    categories.push(...pageItems);
    totalPages = pageTotalPages;
    page += 1;
  }

  if (!lastResponse) {
    throw new Error('标签分类读取失败');
  }
  const data = asRecord(lastResponse.data) ?? {};
  return {
    ...lastResponse,
    data: {
      ...data,
      items: categories,
      categories,
      page: 1,
      size: TAG_CATEGORY_PAGE_SIZE,
      total: data.total ?? categories.length,
      totalPages
    }
  };
}

async function runAction(action: ActionEndpoint) {
  await runWithNotice(async () => {
    const path = resolvePath(action);
    const body = actionRequestBody(action);
    let response: ApiResponse<unknown>;
    if (action.method === 'POST') {
      response = await postJson<unknown>(path, body);
    } else if (action.method === 'PUT') {
      response = await putJson<unknown>(path, body);
    } else if (action.method === 'DELETE') {
      response = await deleteJson<unknown>(path);
    } else {
      response = await getJson<unknown>(path);
    }
    actionResults[action.name] = response;
    await loadSection(selectedSection.value);
  }, `${action.name} 已执行`);
}

async function runWithNotice(task: () => Promise<void>, successMessage: string) {
  pendingOperations += 1;
  loading.value = true;
  notice.value = '';
  try {
    await task();
    noticeKind.value = 'info';
    notice.value = successMessage;
  } catch (error) {
    noticeKind.value = 'error';
    notice.value = error instanceof Error ? error.message : String(error);
  } finally {
    pendingOperations -= 1;
    loading.value = pendingOperations > 0;
  }
}

function resolvePath(action: ActionEndpoint) {
  if (!action.needsId) {
    return action.pathTemplate;
  }
  const id = actionState[action.name].id.trim();
  if (!id) {
    throw new Error('请先填写目标 ID');
  }
  return action.pathTemplate.replace('{id}', encodeURIComponent(id));
}

function parseBody(action: ActionEndpoint) {
  const raw = actionState[action.name].body.trim();
  if (!raw) {
    return undefined;
  }
  try {
    return JSON.parse(raw);
  } catch {
    throw new Error(`${action.name} 的请求体不是合法 JSON`);
  }
}

function actionRequestBody(action: ActionEndpoint) {
  const body = parseBody(action);
  if (action.pathTemplate !== '/admin/api/v1/tags/values' || action.method !== 'POST') {
    return body;
  }
  if (availableTagCategoryId.value == null) {
    throw new Error('没有可用的标签分类，请先创建并启用未合并的分类');
  }
  if (!body || Array.isArray(body) || typeof body !== 'object') {
    throw new Error('创建标签值的请求体必须是 JSON 对象');
  }
  const sanitized = { ...(body as Record<string, unknown>) };
  const requestedCategoryId = Number(sanitized.categoryId);
  const categoryId = availableTagCategoryIds.value.includes(requestedCategoryId)
    ? requestedCategoryId
    : availableTagCategoryId.value;
  if (categoryId == null) {
    throw new Error('没有可用的标签分类，请先创建并启用未合并的分类');
  }
  sanitized.categoryId = categoryId;
  delete sanitized.tagValue;
  return sanitized;
}

function updateAvailableTagCategory(response: ApiResponse<unknown>) {
  const categoryIds = availableTagCategoryIdsFromResponse(response);
  availableTagCategoryIds.value = categoryIds;
  const categoryId = categoryIds[0] ?? null;
  availableTagCategoryId.value = categoryId;
  const action = sections
    .find((section) => section.key === 'rules-tags')
    ?.actions.find((item) => item.pathTemplate === '/admin/api/v1/tags/values' && item.method === 'POST');
  if (!action) return;
  const body = safeParseActionBody(action);
  if (!body || Array.isArray(body) || typeof body !== 'object') return;
  const record = body as Record<string, unknown>;
  if (categoryId == null) {
    delete record.categoryId;
  } else {
    record.categoryId = categoryId;
  }
  actionState[action.name].body = JSON.stringify(record, null, 2);
}

function clearAvailableTagCategories() {
  availableTagCategoryIds.value = [];
  availableTagCategoryId.value = null;
}

function availableTagCategoryIdsFromResponse(response: ApiResponse<unknown>): number[] {
  const data = asRecord(response.data);
  return (extractCategoryItems(data) ?? [])
    .map((candidate) => {
      const category = asRecord(candidate);
      if (!category) return null;
      const enabled = category.isEnabled === true || category.enabled === true;
      const merged = category.merged === true || category.mergedIntoId != null;
      const id = Number(category.id ?? category.categoryId);
      return enabled && !merged && Number.isInteger(id) && id > 0 ? id : null;
    })
    .filter((id): id is number => id != null);
}

function extractCategoryItems(data: Record<string, unknown> | null): unknown[] | null {
  if (!data) return null;
  const page = asRecord(data.page);
  const candidates = [data.items, data.categories, page?.items, page?.categories];
  return candidates.find(Array.isArray) as unknown[] | undefined ?? null;
}

function readTotalPages(data: Record<string, unknown> | null): number | null {
  if (!data) return null;
  const page = asRecord(data.page);
  const value = data.totalPages ?? page?.totalPages;
  const totalPages = Number(value);
  return Number.isInteger(totalPages) && totalPages > 0 ? totalPages : null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function editableFields(action: ActionEndpoint): EditableField[] {
  const body = safeParseActionBody(action);
  if (!body || Array.isArray(body) || typeof body !== 'object') {
    return [];
  }
  return Object.entries(body as Record<string, unknown>)
    .filter(([, value]) => value === null || ['string', 'number', 'boolean'].includes(typeof value))
    .map(([key, value]) => {
      const options = enumOptionsFor(key);
      return {
        key,
        label: fieldLabel(key),
        kind: options.length ? 'enum' : primitiveKind(value),
        value,
        options
      };
    });
}

function updateActionField(action: ActionEndpoint, key: string, value: unknown) {
  const body = safeParseActionBody(action) ?? {};
  if (Array.isArray(body) || typeof body !== 'object') {
    return;
  }
  (body as Record<string, unknown>)[key] = value;
  actionState[action.name].body = JSON.stringify(body, null, 2);
}

function numberFieldValue(value: string): number | null {
  if (value.trim() === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function safeParseActionBody(action: ActionEndpoint): unknown | null {
  const raw = actionState[action.name].body.trim();
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function primitiveKind(value: unknown): EditableField['kind'] {
  if (typeof value === 'boolean') return 'boolean';
  if (typeof value === 'number') return 'number';
  return 'text';
}

function enumOptionsFor(key: string): string[] {
  const options: Record<string, string[]> = {
    role: ['ADMIN', 'LEADER', 'KEEPER'],
    scene: ['OPENING', 'ACTIVE_REPLY', 'REGENERATE'],
    leadType: ['GENERAL', 'TUAN_GOU', 'XIAN_SUO', 'PENDING'],
    contentType: ['TEMPLATE', 'KNOWLEDGE', 'LOCATION', 'IMAGE', 'MINI_PROGRAM'],
    level: ['INFO', 'WARN', 'ERROR'],
    publishType: ['IMMEDIATE', 'SCHEDULED'],
    platform: ['WINDOWS', 'MAC'],
    updateStrategy: ['OPTIONAL', 'FORCE'],
    actionType: ['ALERT', 'TAG_CHANGE', 'NOTIFY_LEADER']
  };
  return options[key] ?? [];
}

function fieldLabel(key: string): string {
  const labels: Record<string, string> = {
    value: '配置值',
    description: '说明',
    skillId: '技能 ID',
    skillName: '技能名称',
    scene: '场景',
    leadType: '线索类型',
    priority: '优先级',
    enabled: '启用',
    envName: '环境名称',
    baseUrl: 'Base URL',
    apiKey: 'API Key',
    name: '名称',
    sheetId: '表格 ID',
    sourceTable: '来源表',
    title: '标题',
    shortcutCode: '快捷码',
    content: '内容',
    imageUrl: '图片 URL',
    sortOrder: '排序',
    phone: '手机号',
    password: '密码',
    displayName: '显示名',
    role: '角色',
    leaderId: '直属组长',
    isEnabled: '启用',
    newPassword: '新密码',
    conditionJson: '条件 JSON',
    actionType: '动作类型',
    actionConfig: '动作配置',
    categoryId: '分类 ID',
    tagValue: '标签值',
    publishType: '发布类型',
    publishAt: '发布时间',
    expireDays: '有效天数',
    version: '版本',
    platform: '平台',
    downloadUrl: '下载地址',
    changelog: '更新说明',
    updateStrategy: '更新策略',
    gradualPercent: '灰度比例',
    fileSize: '文件大小',
    reason: '原因',
    alternativeVersion: '替代版本'
  };
  return labels[key] ?? key;
}

function formatJson(value: unknown) {
  if (value === undefined) {
    return '尚未加载';
  }
  return JSON.stringify(value, null, 2);
}
</script>
