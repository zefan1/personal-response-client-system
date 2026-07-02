import { reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import {
  cleanupExpiredPendingSaves,
  cleanupSaveToTableService,
  getPendingSave,
  recoverPendingSave,
  saveProfile,
  syncProfileToTable
} from '../save-to-table/saveToTableService';
import type { SaveProfileInput } from '../save-to-table/types';
import type {
  Customer,
  CustomerProfileView,
  CustomerSearchResult,
  CustomerSummary,
  ProfileSuggestion,
  RecognizeMultiplePayload,
  SourceFrom,
  StageSuggestPayload
} from './types';

type SectionKey = 'intent' | 'body' | 'followup' | 'suggestions' | 'appointment';

const SEARCH_TIMEOUT_MS = 3000;
const PROFILE_TIMEOUT_MS = 5000;
const SAVE_TIMEOUT_MS = 5000;

export const customerProfileState = reactive({
  keyword: '',
  searchLoading: false,
  searchResults: [] as CustomerSummary[],
  searchTotal: 0,
  searchTruncated: false,
  searchMessage: '',
  candidateVisible: false,
  candidates: [] as CustomerSummary[],
  profileLoading: false,
  profile: null as CustomerProfileView | null,
  fromCache: false,
  offline: false,
  cachedAt: '',
  editMode: false,
  saving: false,
  editFields: {} as Record<string, unknown>,
  pendingSaveBanner: '',
  tableSyncPrompt: null as SaveProfileInput | null,
  generating: false,
  suggestions: [] as ProfileSuggestion[],
  sectionCollapsed: {
    intent: false,
    body: false,
    followup: false,
    suggestions: false,
    appointment: false
  } as Record<SectionKey, boolean>,
  toast: ''
});

let searchTimer: number | null = null;
let searchAbort: AbortController | null = null;
let profileAbort: AbortController | null = null;
let tableSyncTimer: number | null = null;

cleanupExpiredPendingSaves();

export function scheduleSearch(keyword: string): void {
  customerProfileState.keyword = keyword;
  if (searchTimer) {
    window.clearTimeout(searchTimer);
  }
  const config = loadDesktopConfig();
  searchTimer = window.setTimeout(() => {
    void searchCustomers(keyword);
  }, config.searchDebounceMs);
}

export function searchImmediately(keyword: string): void {
  customerProfileState.keyword = keyword;
  if (searchTimer) {
    window.clearTimeout(searchTimer);
    searchTimer = null;
  }
  void searchCustomers(keyword);
}

export async function searchCustomers(keyword: string): Promise<void> {
  const trimmed = keyword.trim();
  if (!trimmed) {
    customerProfileState.searchResults = [];
    customerProfileState.searchTotal = 0;
    customerProfileState.searchMessage = '';
    return;
  }
  searchAbort?.abort();
  searchAbort = new AbortController();
  customerProfileState.searchLoading = true;
  customerProfileState.searchMessage = '';
  try {
    const limit = loadDesktopConfig().searchResultLimit;
    const response = await getJson<CustomerSearchResult>(
      `/api/v1/customers/search?q=${encodeURIComponent(trimmed)}&limit=${limit}`,
      SEARCH_TIMEOUT_MS,
      searchAbort.signal
    );
    if (!response.success || !response.data) {
      customerProfileState.searchMessage = response.errorCode === '40-10001' ? '客户搜索暂不可用，请稍后重试' : '搜索失败，请重试';
      return;
    }
    const { customers, total } = response.data;
    customerProfileState.searchTotal = total;
    customerProfileState.searchTruncated = total > limit;
    if (total === 0) {
      customerProfileState.searchResults = [];
      customerProfileState.searchMessage = '未找到客户，请检查搜索词或确认客户已登记';
    } else if (total === 1 && customers[0]) {
      customerProfileState.searchResults = [];
      await openProfile(customers[0].phone, 'SEARCH');
    } else {
      customerProfileState.searchResults = customers.slice(0, limit);
    }
  } catch {
    customerProfileState.searchMessage = navigator.onLine ? '搜索超时，请重试' : '网络连接中断';
  } finally {
    customerProfileState.searchLoading = false;
  }
}

export async function openProfile(phone: string, sourceFrom: SourceFrom): Promise<void> {
  if (!phone) {
    return;
  }
  profileAbort?.abort();
  profileAbort = new AbortController();
  customerProfileState.profileLoading = true;
  customerProfileState.toast = '';
  try {
    const cached = loadCachedCustomer(phone);
    if (cached) {
      renderProfile(cached.fullProfile, true, false, cached.cachedAt);
    }
    const response = await getJson<CustomerProfileView>(`/api/v1/customers/${encodeURIComponent(phone)}`, PROFILE_TIMEOUT_MS, profileAbort.signal);
    if (!response.success || !response.data) {
      if (response.errorCode === '40-10002') {
        customerProfileState.toast = '该客户档案已被删除，正在返回';
        customerProfileState.profile = null;
      } else {
        customerProfileState.toast = '加载超时，请重试';
      }
      return;
    }
    renderProfile(response.data, false, false, '');
    cacheCustomer(response.data);
    await recoverPendingForProfile(response.data);
    emitCustomerSelected(response.data.customer, sourceFrom);
  } catch {
    const cached = loadCachedCustomer(phone);
    if (cached) {
      renderProfile(cached.fullProfile, true, true, cached.cachedAt);
    } else {
      customerProfileState.toast = navigator.onLine ? '加载超时，请重试' : '当前离线，该客户档案未缓存';
    }
  } finally {
    customerProfileState.profileLoading = false;
  }
}

export function showCandidates(payload: RecognizeMultiplePayload): void {
  const candidates = payload.candidates ?? payload.matchInfo?.customers ?? [];
  customerProfileState.candidateVisible = true;
  customerProfileState.candidates = candidates.slice(0, 5);
}

export function chooseCandidate(candidate: CustomerSummary): void {
  customerProfileState.candidateVisible = false;
  customerProfileState.candidates = [];
  void openProfile(candidate.phone, 'CANDIDATE_LIST');
}

export function dismissCandidates(): void {
  customerProfileState.candidateVisible = false;
  customerProfileState.candidates = [];
  eventBus.emit('customer:selected', { phone: '', scene: 'CHAT_RECOGNIZE', sourceFrom: 'CANDIDATE_DISMISSED' });
}

export async function generateReplyFromProfile(): Promise<void> {
  const customer = customerProfileState.profile?.customer;
  if (!customer?.phone || customerProfileState.generating) {
    return;
  }
  customerProfileState.generating = true;
  eventBus.emit('customer:selected', {
    phone: customer.phone,
    scene: 'ACTIVE_REPLY',
    leadType: customer.leadType ?? '',
    sourceFrom: 'PROFILE_CARD'
  });
  try {
    const response = await postJson('/api/v1/chat/generate', {
      phone: customer.phone,
      scene: 'ACTIVE_REPLY',
      clientMessage: ''
    });
    if (response.success && response.data) {
      eventBus.emit('recognize:result', response.data);
    } else {
      customerProfileState.toast = '生成回复失败，请稍后重试';
    }
  } catch {
    customerProfileState.toast = '生成回复失败，请稍后重试';
  } finally {
    customerProfileState.generating = false;
  }
}

export function enterEditMode(): void {
  const customer = customerProfileState.profile?.customer;
  if (!customer) {
    return;
  }
  customerProfileState.editFields = { ...customer };
  customerProfileState.editMode = true;
}

export function cancelEditMode(): void {
  customerProfileState.editMode = false;
  customerProfileState.editFields = {};
}

export async function saveProfileEdits(): Promise<void> {
  const customer = customerProfileState.profile?.customer;
  if (!customer) {
    return;
  }
  const fields = collectChangedFields(customer, customerProfileState.editFields);
  if (Object.keys(fields).length === 0) {
    cancelEditMode();
    return;
  }
  customerProfileState.saving = true;
  customerProfileState.pendingSaveBanner = '';
  try {
    const input: SaveProfileInput = {
      phone: customer.phone,
      editedFields: fields,
      version: customer.version ?? 0,
      hasTableRow: Boolean(customer.sourceRowId),
      sourceTable: customer.sourceTable,
      sourceRowId: customer.sourceRowId
    };
    const result = await saveProfile(input);
    customerProfileState.toast = result.message;
    if (result.status === 'CONFLICT') {
      const editingSnapshot = { ...customerProfileState.editFields };
      void openProfile(customer.phone, 'PROFILE_CARD');
      customerProfileState.editFields = editingSnapshot;
      customerProfileState.editMode = true;
      return;
    }
    if (result.status === 'BUSY') {
      return;
    }
    if (result.status === 'GIVE_UP') {
      customerProfileState.pendingSaveBanner = result.message;
      return;
    }
    customerProfileState.editMode = false;
    customerProfileState.editFields = {};
    if (result.askTableSync) {
      showTableSyncPrompt(input);
    } else if (result.needRefresh) {
      await openProfile(customer.phone, 'PROFILE_CARD');
    }
  } catch {
    customerProfileState.toast = '保存超时，请重试';
  } finally {
    customerProfileState.saving = false;
  }
}

export async function resolveProfileSuggestion(action: 'CONFIRM' | 'REJECT', suggestion?: ProfileSuggestion): Promise<void> {
  const customer = customerProfileState.profile?.customer;
  const targets = suggestion ? [suggestion] : customerProfileState.suggestions.filter((item) => !item.resolved);
  if (!customer?.phone || targets.length === 0) {
    return;
  }
  targets.forEach((item) => {
    item.resolving = true;
  });
  const suggestionIds = targets.map((item) => item.id ?? item.suggestionId).filter((id): id is number => typeof id === 'number');
  try {
    await postJson(`/api/v1/customers/${encodeURIComponent(customer.phone)}/suggestions/batch-resolve`, {
      action,
      suggestionIds,
      operator: 'desktop'
    }, SAVE_TIMEOUT_MS);
    const ids = new Set(suggestionIds);
    customerProfileState.suggestions = customerProfileState.suggestions.filter((item) => {
      const id = item.id ?? item.suggestionId;
      return typeof id === 'number' ? !ids.has(id) : !targets.includes(item);
    });
  } catch {
    targets.forEach((item) => {
      item.resolving = false;
    });
    customerProfileState.toast = '操作失败，请重试';
  }
}

export function appendProfileSuggestions(payload: { phone?: string; suggestions?: ProfileSuggestion[] }): void {
  const currentPhone = customerProfileState.profile?.customer.phone;
  if (!payload.phone || payload.phone !== currentPhone) {
    return;
  }
  mergeSuggestions(payload.suggestions ?? []);
}

export function appendStageSuggestion(payload: StageSuggestPayload): void {
  const currentPhone = customerProfileState.profile?.customer.phone;
  if (payload.phone !== currentPhone) {
    return;
  }
  mergeSuggestions([{
    suggestionId: payload.suggestionId,
    phone: payload.phone,
    fieldName: 'customerStage',
    currentValue: payload.fromStage,
    suggestedValue: payload.toStage,
    reason: payload.reason
  }]);
}

export function handleStageUpdated(payload: { phone?: string; newStage?: string }): void {
  const customer = customerProfileState.profile?.customer;
  if (!customer || payload.phone !== customer.phone) {
    return;
  }
  customer.customerStage = payload.newStage ?? customer.customerStage;
  customerProfileState.suggestions = customerProfileState.suggestions.filter((item) => item.fieldName !== 'customerStage');
}

export function handleSendConfirmed(payload: { phone?: string }): void {
  const currentPhone = customerProfileState.profile?.customer.phone;
  if (payload.phone && currentPhone && payload.phone.endsWith(currentPhone.slice(-4))) {
    void openProfile(currentPhone, 'PROFILE_CARD');
  }
}

export function cleanupCustomerProfileStore(): void {
  if (searchTimer) {
    window.clearTimeout(searchTimer);
    searchTimer = null;
  }
  searchAbort?.abort();
  profileAbort?.abort();
  if (tableSyncTimer) {
    window.clearTimeout(tableSyncTimer);
    tableSyncTimer = null;
  }
  cleanupSaveToTableService();
}

export async function confirmTableSync(): Promise<void> {
  const input = customerProfileState.tableSyncPrompt;
  if (!input) {
    return;
  }
  clearTableSyncPrompt();
  const result = await syncProfileToTable(input);
  customerProfileState.toast = result.message;
  if (result.needRefresh) {
    await openProfile(input.phone, 'PROFILE_CARD');
  }
}

export async function skipTableSync(): Promise<void> {
  const input = customerProfileState.tableSyncPrompt;
  clearTableSyncPrompt();
  if (input) {
    await openProfile(input.phone, 'PROFILE_CARD');
  }
}

function renderProfile(profile: CustomerProfileView, fromCache: boolean, offline: boolean, cachedAt: string): void {
  customerProfileState.profile = profile;
  customerProfileState.fromCache = fromCache;
  customerProfileState.offline = offline;
  customerProfileState.cachedAt = cachedAt;
  customerProfileState.suggestions = (profile.pendingSuggestions ?? []).map((item) => ({ ...item, resolved: false, resolving: false }));
  customerProfileState.pendingSaveBanner = getPendingSave(profile.customer.phone) ? '上次编辑内容未保存成功，系统将在稍后自动重试' : '';
  resetSectionState();
}

function emitCustomerSelected(customer: Customer, sourceFrom: SourceFrom): void {
  eventBus.emit('customer:selected', {
    phone: customer.phone,
    scene: sourceFrom === 'PROFILE_CARD' ? 'ACTIVE_REPLY' : 'CHAT_RECOGNIZE',
    leadType: customer.leadType ?? '',
    sourceFrom
  });
}

function mergeSuggestions(incoming: ProfileSuggestion[]): void {
  const existingKeys = new Set(customerProfileState.suggestions.map((item) => suggestionKey(item)));
  incoming.forEach((item) => {
    const key = suggestionKey(item);
    if (!existingKeys.has(key)) {
      customerProfileState.suggestions.push({ ...item, resolved: false, resolving: false });
      existingKeys.add(key);
    }
  });
  customerProfileState.sectionCollapsed.suggestions = false;
}

function suggestionKey(item: ProfileSuggestion): string {
  return `${item.fieldName}:${String(item.suggestedValue)}`;
}

function collectChangedFields(original: Customer, edited: Record<string, unknown>): Record<string, unknown> {
  const fields: Record<string, unknown> = {};
  Object.entries(edited).forEach(([key, value]) => {
    if (['id', 'phone', 'version', 'createdAt', 'updatedAt'].includes(key)) {
      return;
    }
    if (value !== (original as unknown as Record<string, unknown>)[key]) {
      fields[key] = value;
    }
  });
  return fields;
}

function resetSectionState(): void {
  customerProfileState.sectionCollapsed.intent = false;
  customerProfileState.sectionCollapsed.body = false;
  customerProfileState.sectionCollapsed.followup = false;
  customerProfileState.sectionCollapsed.suggestions = customerProfileState.suggestions.length === 0;
  customerProfileState.sectionCollapsed.appointment = false;
}

function cacheCustomer(profile: CustomerProfileView): void {
  try {
    const key = `customer_cache:${profile.customer.phone}`;
    localStorage.setItem(key, JSON.stringify({
      phone: profile.customer.phone,
      fullProfile: profile,
      cachedAt: new Date().toISOString(),
      lastViewedAt: new Date().toISOString()
    }));
    enforceCacheLimit();
  } catch {
    // Cache is optional; online profile rendering remains available.
  }
}

function loadCachedCustomer(phone: string): { fullProfile: CustomerProfileView; cachedAt: string } | null {
  try {
    const raw = localStorage.getItem(`customer_cache:${phone}`);
    return raw ? JSON.parse(raw) as { fullProfile: CustomerProfileView; cachedAt: string } : null;
  } catch {
    return null;
  }
}

function enforceCacheLimit(): void {
  const limit = loadDesktopConfig().customerCacheLimit;
  const items: Array<{ key: string; lastViewedAt: string }> = [];
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (!key?.startsWith('customer_cache:')) {
      continue;
    }
    const raw = localStorage.getItem(key);
    if (!raw) {
      continue;
    }
    try {
      items.push({ key, lastViewedAt: (JSON.parse(raw) as { lastViewedAt?: string }).lastViewedAt ?? '' });
    } catch {
      localStorage.removeItem(key);
    }
  }
  items.sort((a, b) => a.lastViewedAt.localeCompare(b.lastViewedAt));
  items.slice(0, Math.max(0, items.length - limit)).forEach((item) => localStorage.removeItem(item.key));
}

async function recoverPendingForProfile(profile: CustomerProfileView): Promise<void> {
  const pending = getPendingSave(profile.customer.phone);
  if (!pending) {
    return;
  }
  customerProfileState.pendingSaveBanner = '上次编辑内容未保存成功，系统正在自动重试';
  const result = await recoverPendingSave(profile.customer.phone, profile.customer.version ?? pending.version);
  if (result?.status === 'OK') {
    customerProfileState.pendingSaveBanner = '';
    customerProfileState.toast = '上次未保存内容已恢复保存';
  } else if (result) {
    customerProfileState.pendingSaveBanner = result.message;
  }
}

function showTableSyncPrompt(input: SaveProfileInput): void {
  clearTableSyncPrompt();
  customerProfileState.tableSyncPrompt = input;
  tableSyncTimer = window.setTimeout(() => {
    void skipTableSync();
  }, 15000);
}

function clearTableSyncPrompt(): void {
  if (tableSyncTimer) {
    window.clearTimeout(tableSyncTimer);
    tableSyncTimer = null;
  }
  customerProfileState.tableSyncPrompt = null;
}
