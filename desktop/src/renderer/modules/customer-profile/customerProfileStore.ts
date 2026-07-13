import { reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import { getAlertsByPhone, loadAlertsByPhone } from '../abnormal-alert/alertStore';
import {
  cleanupExpiredPendingSaves,
  cleanupSaveToTableService,
  getPendingSave,
  recoverPendingSave,
  saveProfile,
  syncProfileToTable
} from '../save-to-table/saveToTableService';
import {
  confirmStageSuggestion,
  handleCustomerProfileLoaded,
  ignoreStageSuggestion
} from '../stage-suggestion/stageSuggestionHandler';
import type { SaveProfileInput } from '../save-to-table/types';
import type {
  AbnormalAlertPayload,
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
type TableSyncStatusLevel = 'pending' | 'syncing' | 'success' | 'retrying' | 'skipped';

type TableSyncStatus = {
  phone: string;
  level: TableSyncStatusLevel;
  message: string;
  detail?: string;
};

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
  candidateSessionId: '',
  profileLoading: false,
  profile: null as CustomerProfileView | null,
  fromCache: false,
  offline: false,
  cachedAt: '',
  editMode: false,
  saving: false,
  editFields: {} as Record<string, unknown>,
  pendingSaveBanner: '',
  tableSyncStatus: null as TableSyncStatus | null,
  profileAlert: null as AbnormalAlertPayload | null,
  tableSyncPrompt: null as SaveProfileInput | null,
  activeReplySessionId: '',
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
    clearSearchResults();
    return;
  }
  searchAbort?.abort();
  searchAbort = new AbortController();
  clearSearchResults();
  customerProfileState.searchLoading = true;
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
      await openProfile(summaryPhone(customers[0]), 'SEARCH');
    } else {
      customerProfileState.searchResults = customers.slice(0, limit);
    }
  } catch {
    customerProfileState.searchMessage = navigator.onLine ? '搜索超时，请重试' : '网络连接中断';
  } finally {
    customerProfileState.searchLoading = false;
  }
}

export async function openProfile(phone: string, sourceFrom: SourceFrom, sessionId = ''): Promise<void> {
  if (!phone) {
    return;
  }
  if (!isSamePhone(phone, currentProfilePhone())) {
    clearTableSyncStatus();
  }
  clearSearchResults();
  profileAbort?.abort();
  profileAbort = new AbortController();
  customerProfileState.profileLoading = true;
  if (sessionId) {
    customerProfileState.activeReplySessionId = sessionId;
  } else if (sourceFrom !== 'CANDIDATE_LIST') {
    customerProfileState.activeReplySessionId = '';
  }
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
    await refreshProfileAlert(profilePhone(response.data));
    cacheCustomer(response.data);
    handleCustomerProfileLoaded(response.data);
    await recoverPendingForProfile(response.data);
    emitCustomerSelected(response.data.customer, sourceFrom, sessionId);
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
  customerProfileState.candidateSessionId = payload.sessionId ?? '';
}

export function chooseCandidate(candidate: CustomerSummary): void {
  const sessionId = customerProfileState.candidateSessionId;
  customerProfileState.candidateVisible = false;
  customerProfileState.candidates = [];
  customerProfileState.candidateSessionId = '';
  void openProfile(summaryPhone(candidate), 'CANDIDATE_LIST', sessionId);
}

export function dismissCandidates(): void {
  const sessionId = customerProfileState.candidateSessionId;
  customerProfileState.candidateVisible = false;
  customerProfileState.candidates = [];
  customerProfileState.candidateSessionId = '';
  eventBus.emit('customer:selected', { ...(sessionId ? { sessionId } : {}), phone: '', scene: 'CHAT_RECOGNIZE', sourceFrom: 'CANDIDATE_DISMISSED' });
}

export async function generateReplyFromProfile(): Promise<void> {
  const customer = customerProfileState.profile?.customer;
  const phone = currentProfilePhone();
  if (!customer || !phone || customerProfileState.generating) {
    return;
  }
  customerProfileState.generating = true;
  eventBus.emit('customer:selected', {
    ...(customerProfileState.activeReplySessionId ? { sessionId: customerProfileState.activeReplySessionId } : {}),
    phone,
    scene: 'ACTIVE_REPLY',
    leadType: customer.leadType ?? '',
    sourceFrom: 'PROFILE_CARD'
  });
  try {
    const response = await postJson('/api/v1/chat/generate', {
      phone,
      scene: 'ACTIVE_REPLY',
      clientMessage: ''
    });
    if (response.success && response.data) {
      eventBus.emit('recognize:result', {
        ...(customerProfileState.activeReplySessionId ? { sessionId: customerProfileState.activeReplySessionId } : {}),
        source: 'PROFILE_CARD',
        response: response.data
      });
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
  const phone = currentProfilePhone();
  if (!customer || !phone) {
    return;
  }
  const fields = collectChangedFields(customer, customerProfileState.editFields);
  if (Object.keys(fields).length === 0) {
    customerProfileState.toast = '没有改动需要保存';
    cancelEditMode();
    return;
  }
  customerProfileState.saving = true;
  customerProfileState.pendingSaveBanner = '';
  clearTableSyncStatus();
  try {
    const input: SaveProfileInput = {
      phone,
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
      void openProfile(phone, 'PROFILE_CARD');
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
    if (result.needRefresh) {
      const message = result.message;
      await openProfile(phone, 'PROFILE_CARD');
      customerProfileState.toast = message;
    }
    if (result.askTableSync) {
      showTableSyncPrompt(input);
      setTableSyncStatus(input.phone, 'pending', '档案已保存，等待同步企微表格', '确认同步后会写回该客户的原表格行');
      customerProfileState.toast = result.message;
    }
  } catch {
    customerProfileState.toast = '保存超时，请重试';
  } finally {
    customerProfileState.saving = false;
  }
}

export async function resolveProfileSuggestion(action: 'CONFIRM' | 'REJECT', suggestion?: ProfileSuggestion): Promise<void> {
  const targets = suggestion ? [suggestion] : customerProfileState.suggestions.filter((item) => !item.resolved);
  const phone = currentProfilePhone();
  if (!phone || targets.length === 0) {
    return;
  }
  targets.forEach((item) => {
    item.resolving = true;
  });
  const stageTargets = targets.filter((item) => item.suggestionType === 'STAGE_CHANGE' || item.fieldName === 'customerStage');
  if (stageTargets.length > 0) {
    for (const item of stageTargets) {
      const ok = action === 'CONFIRM' ? await confirmStageSuggestion(item) : await ignoreStageSuggestion(item);
      if (ok) {
        item.resolved = true;
      } else {
        item.resolving = false;
        customerProfileState.toast = '阶段变更保存失败，请稍后重试';
      }
    }
    customerProfileState.suggestions = customerProfileState.suggestions.filter((item) => !item.resolved);
    return;
  }
  const suggestionIds = targets.map((item) => item.id ?? item.suggestionId).filter((id): id is number => typeof id === 'number');
  try {
    await postJson(`/api/v1/customers/${encodeURIComponent(phone)}/suggestions/batch-resolve`, {
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
  const currentPhone = currentProfilePhone();
  if (!payload.phone || payload.phone !== currentPhone) {
    return;
  }
  mergeSuggestions(payload.suggestions ?? []);
}

export function appendStageSuggestion(payload: StageSuggestPayload): void {
  const currentPhone = currentProfilePhone();
  if (payload.phone !== currentPhone) {
    return;
  }
  mergeSuggestions([{
    suggestionId: payload.suggestionId,
    phone: payload.phone,
    fieldName: 'customerStage',
    suggestionType: 'STAGE_CHANGE',
    currentValue: payload.fromStage,
    suggestedValue: payload.toStage,
    fromStage: payload.fromStage,
    toStage: payload.toStage,
    reason: payload.reason,
    stageOptionMatch: payload.stageOptionMatch,
    validOptions: payload.validOptions,
    createdAt: payload.createdAt
  }]);
}

export function handleProfileAbnormalAlert(payload: AbnormalAlertPayload): void {
  const currentPhone = currentProfilePhone();
  if (!currentPhone || payload.phone !== currentPhone) {
    return;
  }
  customerProfileState.profileAlert = payload.acknowledged ? null : payload;
}

export function handleStageUpdated(payload: { phone?: string; newStage?: string }): void {
  const customer = customerProfileState.profile?.customer;
  if (!customer || payload.phone !== currentProfilePhone()) {
    return;
  }
  customer.customerStage = payload.newStage ?? customer.customerStage;
  customerProfileState.suggestions = customerProfileState.suggestions.filter((item) => item.fieldName !== 'customerStage');
}

export function handleSendConfirmed(payload: { phone?: string }): void {
  const currentPhone = currentProfilePhone();
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
  setTableSyncStatus(input.phone, 'syncing', '正在同步到企微表格', '请稍候，完成后会刷新档案');
  const result = await syncProfileToTable(input);
  const message = result.message;
  if (result.status === 'OK') {
    setTableSyncStatus(input.phone, 'success', message || '已同步到表格', '表格和本地档案已进入同一轮刷新');
  } else {
    setTableSyncStatus(input.phone, 'retrying', message || '表格同步失败，系统将在后台自动重试', '无需重复保存，可稍后刷新确认');
  }
  customerProfileState.toast = message;
  if (result.needRefresh) {
    await openProfile(input.phone, 'PROFILE_CARD');
    customerProfileState.toast = message;
  }
}

export async function skipTableSync(): Promise<void> {
  const input = customerProfileState.tableSyncPrompt;
  clearTableSyncPrompt();
  if (input) {
    setTableSyncStatus(input.phone, 'skipped', '已暂不同步企微表格', '本地档案已保存，表格保留原值');
    await openProfile(input.phone, 'PROFILE_CARD');
  }
}

function renderProfile(profile: CustomerProfileView, fromCache: boolean, offline: boolean, cachedAt: string): void {
  customerProfileState.profile = profile;
  const phone = profilePhone(profile);
  customerProfileState.profileAlert = getAlertsByPhone(phone)[0] ?? null;
  customerProfileState.fromCache = fromCache;
  customerProfileState.offline = offline;
  customerProfileState.cachedAt = cachedAt;
  customerProfileState.suggestions = (profile.pendingSuggestions ?? []).map((item) => ({ ...item, resolved: false, resolving: false }));
  customerProfileState.pendingSaveBanner = getPendingSave(phone) ? '上次编辑内容未保存成功，系统将在稍后自动重试' : '';
  resetSectionState();
}

async function refreshProfileAlert(phone: string): Promise<void> {
  const alerts = await loadAlertsByPhone(phone);
  customerProfileState.profileAlert = alerts[0] ?? null;
}

function emitCustomerSelected(customer: Customer, sourceFrom: SourceFrom, sessionId = ''): void {
  const phone = customerPhone(customer);
  eventBus.emit('customer:selected', {
    ...(sessionId ? { sessionId } : {}),
    phone,
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
    if (['id', 'phone', 'phoneFull', 'version', 'createdAt', 'updatedAt'].includes(key)) {
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

function clearSearchResults(): void {
  customerProfileState.searchResults = [];
  customerProfileState.searchTotal = 0;
  customerProfileState.searchTruncated = false;
  customerProfileState.searchMessage = '';
}

function cacheCustomer(profile: CustomerProfileView): void {
  try {
    const phone = profilePhone(profile);
    const key = `customer_cache:${phone}`;
    localStorage.setItem(key, JSON.stringify({
      phone,
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
  const phone = profilePhone(profile);
  const pending = getPendingSave(phone);
  if (!pending) {
    return;
  }
  customerProfileState.pendingSaveBanner = '上次编辑内容未保存成功，系统正在自动重试';
  const result = await recoverPendingSave(phone, profile.customer.version ?? pending.version);
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

function setTableSyncStatus(phone: string, level: TableSyncStatusLevel, message: string, detail?: string): void {
  customerProfileState.tableSyncStatus = { phone, level, message, detail };
}

function clearTableSyncStatus(): void {
  customerProfileState.tableSyncStatus = null;
}

function summaryPhone(customer: CustomerSummary): string {
  return customer.phoneFull || customer.phone;
}

function customerPhone(customer: Customer): string {
  return customer.phoneFull || customerProfileState.profile?.phoneFull || customer.phone;
}

function profilePhone(profile: CustomerProfileView): string {
  return profile.phoneFull || profile.customer.phoneFull || profile.customer.phone;
}

function currentProfilePhone(): string {
  const profile = customerProfileState.profile;
  return profile ? profilePhone(profile) : '';
}

function isSamePhone(left: string, right: string): boolean {
  if (!left || !right) {
    return false;
  }
  return left === right || left.endsWith(right.slice(-4)) || right.endsWith(left.slice(-4));
}
