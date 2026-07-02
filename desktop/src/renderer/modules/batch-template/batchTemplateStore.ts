import { computed, reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import type { Customer, CustomerProfileView } from '../customer-profile/types';
import type { QuickSearchItem } from '../quick-search/types';
import type { BatchCustomerState, BatchCustomersResponse, BatchPhase, BatchStartPayload, BatchTemplate } from './types';

const TEMPLATE_CACHE_KEY = 'batch_template_cache';
const CUSTOMER_GET_TIMEOUT_MS = 2000;
const TEMPLATE_TIMEOUT_MS = 3000;
const SEND_CONFIRM_TIMEOUT_MS = 2000;

export const batchTemplateState = reactive({
  phase: 'IDLE' as BatchPhase,
  source: 'FOLLOWUP_LIST',
  phones: [] as string[],
  customers: [] as BatchCustomerState[],
  templates: [] as BatchTemplate[],
  selectedTemplateId: null as number | null,
  currentIndex: 0,
  loadingTemplates: false,
  loadingCustomers: false,
  error: '',
  toast: '',
  filterScene: '全部',
  localLogs: [] as Array<{ timestamp: number; phoneTail: string; templateId: number | null; result: string; errorMessage?: string }>
});

export const visibleBatchTemplates = computed(() => {
  const enabled = batchTemplateState.templates.filter((item) => item.isEnabled && item.contentType === 'TEMPLATE');
  if (batchTemplateState.filterScene === '全部') {
    return enabled;
  }
  return enabled.filter((item) => item.scene === batchTemplateState.filterScene);
});

export const selectedTemplate = computed(() =>
  batchTemplateState.templates.find((item) => item.id === batchTemplateState.selectedTemplateId) ?? null
);

export const currentBatchCustomer = computed(() => batchTemplateState.customers[batchTemplateState.currentIndex] ?? null);
export const totalBatchCount = computed(() => batchTemplateState.customers.length);
export const copiedBatchCount = computed(() => batchTemplateState.customers.filter((item) => item.copied).length);
export const processedBatchCount = computed(() => Math.min(batchTemplateState.currentIndex, totalBatchCount.value));
export const remainingBatchCount = computed(() => Math.max(0, totalBatchCount.value - copiedBatchCount.value));
export const batchProgressPercent = computed(() => totalBatchCount.value ? Math.round((processedBatchCount.value / totalBatchCount.value) * 100) : 0);
export const filledTemplateText = computed(() => {
  const template = selectedTemplate.value;
  const current = currentBatchCustomer.value;
  if (!template) {
    return '';
  }
  if (!current?.profile?.customer) {
    return '';
  }
  if (!template.content) {
    return '模板内容为空，请在运营后台检查。';
  }
  return fillTemplate(template.content, current.profile.customer);
});

export async function startBatchTemplateFlow(payload: BatchStartPayload): Promise<void> {
  const max = loadDesktopConfig().batchMaxCustomers;
  const phones = uniquePhones(payload.phones);
  batchTemplateState.error = '';
  batchTemplateState.toast = '';
  if (phones.length === 0) {
    return;
  }
  if (phones.length > max) {
    batchTemplateState.toast = `每次最多批量处理 ${max} 个客户，请分批操作`;
    return;
  }
  batchTemplateState.phase = 'SELECT_TEMPLATE';
  batchTemplateState.source = payload.source;
  batchTemplateState.phones = phones;
  batchTemplateState.customers = phones.map((phone) => ({ phone, profile: null, copied: false, skipped: false }));
  batchTemplateState.currentIndex = 0;
  await Promise.all([loadBatchTemplates(), loadBatchCustomers()]);
  autoSelectTemplate();
}

export async function loadBatchTemplates(): Promise<void> {
  batchTemplateState.loadingTemplates = true;
  try {
    const response = await getJson<QuickSearchItem[]>('/api/v1/quick-search/items?contentType=TEMPLATE&enabled=true', TEMPLATE_TIMEOUT_MS);
    if (response.success && response.data) {
      batchTemplateState.templates = response.data.filter((item) => item.contentType === 'TEMPLATE' && item.isEnabled);
      writeTemplateCache(batchTemplateState.templates);
      return;
    }
    readTemplateCache();
  } catch {
    readTemplateCache();
  } finally {
    batchTemplateState.loadingTemplates = false;
  }
}

export async function loadBatchCustomers(): Promise<void> {
  batchTemplateState.loadingCustomers = true;
  try {
    const response = await postJson<BatchCustomersResponse>(
      '/api/v1/customers/batch',
      { phones: batchTemplateState.phones },
      loadDesktopConfig().batchCustomerBatchTimeoutMs
    );
    if (response.success && response.data) {
      applyCustomerProfiles(response.data.customers);
      return;
    }
    await loadCustomersOneByOne();
  } catch {
    await loadCustomersOneByOne();
  } finally {
    batchTemplateState.loadingCustomers = false;
  }
}

export function selectBatchTemplate(templateId: number): void {
  batchTemplateState.selectedTemplateId = templateId;
}

export function confirmBatchTemplate(): void {
  if (!selectedTemplate.value) {
    batchTemplateState.toast = '请选择一个模板';
    return;
  }
  batchTemplateState.phase = 'SENDING';
  skipUnavailableForward();
}

export async function copyCurrentBatchText(): Promise<void> {
  const current = currentBatchCustomer.value;
  const template = selectedTemplate.value;
  const text = filledTemplateText.value;
  if (!current || !template || !text || current.copied) {
    return;
  }
  const result = await window.desktopBridge.writeClipboardText(text);
  if (!result.success) {
    recordLocalLog(current.phone, template.id, 'COPY_FAILED', result.error);
    batchTemplateState.toast = '复制失败，请重试';
    return;
  }
  current.copied = true;
  recordLocalLog(current.phone, template.id, 'COPIED');
  void sendBatchConfirm(current, template, text);
}

export async function copyBatchCustomerField(value: string, label: string): Promise<void> {
  if (!value) {
    return;
  }
  const result = await window.desktopBridge.writeClipboardText(value);
  batchTemplateState.toast = result.success ? `已复制${label}` : '复制失败，请重试';
}

export function nextBatchCustomer(): void {
  if (batchTemplateState.currentIndex < totalBatchCount.value) {
    batchTemplateState.currentIndex += 1;
  }
  if (batchTemplateState.currentIndex >= totalBatchCount.value) {
    batchTemplateState.phase = 'COMPLETED';
    return;
  }
  skipUnavailableForward();
}

export function previousBatchCustomer(): void {
  batchTemplateState.currentIndex = Math.max(0, batchTemplateState.currentIndex - 1);
}

export function pauseBatchTemplate(): void {
  batchTemplateState.phase = 'PAUSED';
}

export function resumeBatchTemplate(): void {
  batchTemplateState.phase = 'SENDING';
  skipUnavailableForward();
}

export function exitBatchTemplate(): void {
  batchTemplateState.phase = 'IDLE';
  batchTemplateState.phones = [];
  batchTemplateState.customers = [];
  batchTemplateState.currentIndex = 0;
  batchTemplateState.selectedTemplateId = null;
  batchTemplateState.error = '';
}

export function setBatchSceneFilter(scene: string): void {
  batchTemplateState.filterScene = scene;
}

function applyCustomerProfiles(profiles: CustomerProfileView[]): void {
  const byPhone = new Map<string, CustomerProfileView>();
  profiles.forEach((profile) => {
    const phone = unmaskPhone(profile.customer.phone);
    byPhone.set(phone, profile);
  });
  batchTemplateState.customers = batchTemplateState.phones.map((phone) => {
    const profile = byPhone.get(phone) ?? profiles.find((item) => item.customer.phone === phone || item.customer.phone.endsWith(phone.slice(-4))) ?? null;
    return { phone, profile, copied: false, skipped: profile === null };
  });
}

async function loadCustomersOneByOne(): Promise<void> {
  const next: BatchCustomerState[] = [];
  for (const phone of batchTemplateState.phones) {
    try {
      const response = await getJson<CustomerProfileView>(`/api/v1/customers/${encodeURIComponent(phone)}`, CUSTOMER_GET_TIMEOUT_MS);
      next.push({ phone, profile: response.success ? response.data : null, copied: false, skipped: !response.success || !response.data });
    } catch {
      next.push({ phone, profile: null, copied: false, skipped: true });
    }
  }
  batchTemplateState.customers = next;
}

async function sendBatchConfirm(current: BatchCustomerState, template: BatchTemplate, sentText: string): Promise<void> {
  try {
    const customer = current.profile?.customer;
    await postJson('/api/v1/chat/send-confirm', {
      phone: current.phone,
      nickname: customer?.nickname ?? '',
      isNewCustomer: false,
      sourceTable: customer?.sourceTable ?? '',
      leadType: customer?.leadType ?? '',
      conversationSummary: sentText,
      rawMessages: [],
      sentText,
      selectedDirection: 'BATCH_TEMPLATE',
      source: 'BATCH_TEMPLATE',
      templateId: template.id
    }, SEND_CONFIRM_TIMEOUT_MS);
  } catch (error) {
    recordLocalLog(current.phone, template.id, 'SEND_CONFIRM_FAILED', error instanceof Error ? error.message : 'send confirm failed');
  }
}

function fillTemplate(content: string, customer: Customer): string {
  const values = new Map<string, string | null | undefined>([
    ['客户昵称', customer.nickname],
    ['预约时间', formatAppointmentDate(customer.appointmentDate)],
    ['预约门店', customer.appointmentStore],
    ['预约项目', customer.appointmentItem],
    ['管家名', customer.assignedKeeper],
    ['意向门店', customer.intendedStore],
    ['手机后4位', unmaskPhone(customer.phone).slice(-4)]
  ]);
  return content.replace(/\{[^{}]+\}/g, (token) => {
    const key = token.slice(1, -1);
    const value = values.get(key);
    return value ? value : token;
  });
}

function autoSelectTemplate(): void {
  const templates = batchTemplateState.templates.filter((item) => item.isEnabled && item.contentType === 'TEMPLATE');
  if (templates.length === 0) {
    batchTemplateState.selectedTemplateId = null;
    batchTemplateState.error = '无法加载模板，请检查网络后重试';
    return;
  }
  const customers = batchTemplateState.customers.map((item) => item.profile?.customer).filter(Boolean) as Customer[];
  if (customers.length < 3) {
    batchTemplateState.selectedTemplateId = templates[0].id;
    return;
  }
  const leadType = dominantLeadType(customers);
  const scene = dominantScene(customers);
  const ranked = [...templates].sort((left, right) => scoreTemplate(right, leadType, scene) - scoreTemplate(left, leadType, scene));
  batchTemplateState.selectedTemplateId = ranked[0].id;
}

function dominantLeadType(customers: Customer[]): string {
  const counts = countBy(customers.map((item) => item.leadType ?? ''));
  const total = customers.length;
  for (const value of ['TUAN_GOU', 'XIAN_SUO']) {
    if ((counts.get(value) ?? 0) / total >= 0.6) {
      return value;
    }
  }
  return 'GENERAL';
}

function dominantScene(customers: Customer[]): string {
  const today = new Date().toISOString().slice(0, 10);
  const appointmentToday = customers.filter((item) => item.appointmentDate === today).length;
  const overdue = customers.filter((item) => item.nextFollowupAt && new Date(item.nextFollowupAt).getTime() < Date.now()).length;
  if (overdue / customers.length >= 0.5) {
    return '催约';
  }
  if (appointmentToday / customers.length >= 0.5) {
    return '预约提醒';
  }
  return '';
}

function scoreTemplate(template: BatchTemplate, leadType: string, scene: string): number {
  let score = 0;
  if (template.leadType === leadType || template.leadType === 'GENERAL') {
    score += 2;
  }
  if (scene && template.scene === scene) {
    score += 2;
  }
  return score;
}

function skipUnavailableForward(): void {
  while (batchTemplateState.currentIndex < totalBatchCount.value && !currentBatchCustomer.value?.profile) {
    if (currentBatchCustomer.value) {
      currentBatchCustomer.value.skipped = true;
    }
    batchTemplateState.currentIndex += 1;
  }
  if (batchTemplateState.currentIndex >= totalBatchCount.value) {
    batchTemplateState.phase = 'COMPLETED';
  }
}

function uniquePhones(phones: string[]): string[] {
  return [...new Set(phones.map((phone) => phone.trim()).filter(Boolean))];
}

function unmaskPhone(phone: string): string {
  return phone.replace(/\*/g, '');
}

function formatAppointmentDate(value?: string | null): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

function countBy(values: string[]): Map<string, number> {
  const counts = new Map<string, number>();
  values.forEach((value) => counts.set(value, (counts.get(value) ?? 0) + 1));
  return counts;
}

function recordLocalLog(phone: string, templateId: number | null, result: string, errorMessage?: string): void {
  batchTemplateState.localLogs.unshift({
    timestamp: Date.now(),
    phoneTail: phone.slice(-4),
    templateId,
    result,
    errorMessage
  });
  batchTemplateState.localLogs = batchTemplateState.localLogs.slice(0, 500);
}

function readTemplateCache(): void {
  try {
    const raw = localStorage.getItem(TEMPLATE_CACHE_KEY);
    batchTemplateState.templates = raw ? JSON.parse(raw) as BatchTemplate[] : [];
  } catch {
    batchTemplateState.templates = [];
  }
}

function writeTemplateCache(items: BatchTemplate[]): void {
  try {
    localStorage.setItem(TEMPLATE_CACHE_KEY, JSON.stringify(items));
  } catch {
    // Cache write failure should not block the active batch flow.
  }
}
