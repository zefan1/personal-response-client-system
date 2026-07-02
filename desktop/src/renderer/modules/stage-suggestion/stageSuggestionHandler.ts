import { getJson, postJson, putJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import { customerProfileState } from '../customer-profile/customerProfileStore';
import type { CustomerProfileView, ProfileSuggestion, StageSuggestPayload } from '../customer-profile/types';

type ProfileSuggestionsPayload = {
  phone?: string;
  suggestions?: ProfileSuggestion[];
};

const pendingStageSuggestions = new Map<string, { suggestion: ProfileSuggestion; expiresAt: number }>();
const emittedSuggestionIds = new Set<string>();
const retryTimers = new Set<number>();
let currentPhone = '';
let initialized = false;
let disposeProfileSuggestions: (() => void) | null = null;
let disposeCustomerSelected: (() => void) | null = null;

export function initializeStageSuggestionHandler(): void {
  if (initialized) {
    return;
  }
  initialized = true;
  disposeProfileSuggestions = eventBus.on<ProfileSuggestionsPayload>('PROFILE_SUGGESTIONS', handleProfileSuggestions);
  disposeCustomerSelected = eventBus.on<{ phone?: string }>('customer:selected', (payload) => {
    currentPhone = payload.phone ?? '';
    flushPendingForPhone(currentPhone);
  });
}

export function cleanupStageSuggestionHandler(): void {
  disposeProfileSuggestions?.();
  disposeCustomerSelected?.();
  disposeProfileSuggestions = null;
  disposeCustomerSelected = null;
  pendingStageSuggestions.clear();
  emittedSuggestionIds.clear();
  retryTimers.forEach((timer) => window.clearTimeout(timer));
  retryTimers.clear();
  initialized = false;
}

export function handleCustomerProfileLoaded(profile: CustomerProfileView): void {
  currentPhone = profile.customer.phone;
  (profile.pendingSuggestions ?? []).forEach((suggestion) => {
    if (suggestion.fieldName === 'customerStage') {
      emitOrPend(profile.customer.phone, suggestion);
    }
  });
}

export async function confirmStageSuggestion(suggestion: ProfileSuggestion): Promise<boolean> {
  const phone = suggestion.phone ?? customerProfileState.profile?.customer.phone;
  const newStage = suggestion.toStage ?? String(suggestion.suggestedValue ?? '');
  const version = customerProfileState.profile?.customer.version ?? 0;
  if (!phone || !newStage) {
    return false;
  }
  for (let attempt = 0; attempt <= loadDesktopConfig().saveMaxRetries; attempt += 1) {
    try {
      const response = await putJson(`/api/v1/customers/${encodeURIComponent(phone)}`, {
        version,
        fields: { customerStage: newStage },
        operator: 'desktop'
      }, loadDesktopConfig().saveToTableTimeoutMs);
      if (response.success) {
        eventBus.emit('stage:updated', { phone, newStage });
        return true;
      }
      if (response.errorCode === '50-10002') {
        await refreshProfile(phone);
        return customerProfileState.profile?.customer.customerStage === newStage;
      }
    } catch {
      // Retry below.
    }
    if (attempt < loadDesktopConfig().saveMaxRetries) {
      await wait(loadDesktopConfig().saveRetryIntervalMs);
    }
  }
  return false;
}

export async function ignoreStageSuggestion(suggestion: ProfileSuggestion): Promise<boolean> {
  const phone = suggestion.phone ?? customerProfileState.profile?.customer.phone;
  const id = suggestion.id ?? suggestion.suggestionId;
  if (!phone || typeof id !== 'number') {
    return true;
  }
  try {
    await postJson(`/api/v1/customers/${encodeURIComponent(phone)}/suggestions/batch-resolve`, {
      action: 'REJECT',
      suggestionIds: [id],
      operator: 'desktop'
    }, 5000);
  } catch {
    // Ignore is a UI intent; stale backend state can reappear on the next GET.
  }
  return true;
}

function handleProfileSuggestions(payload: ProfileSuggestionsPayload): void {
  if (!payload.phone || !payload.suggestions?.length) {
    return;
  }
  payload.suggestions.forEach((suggestion) => {
    if (suggestion.fieldName === 'customerStage') {
      emitOrPend(payload.phone ?? '', suggestion);
    }
  });
}

function emitOrPend(phone: string, suggestion: ProfileSuggestion): void {
  if (!phone || !suggestion.suggestedValue) {
    return;
  }
  const key = suggestionKey(phone, suggestion);
  if (emittedSuggestionIds.has(key)) {
    return;
  }
  if (currentPhone && currentPhone !== phone) {
    pendingStageSuggestions.set(phone, {
      suggestion,
      expiresAt: Date.now() + loadDesktopConfig().stageSuggestPendingTtlS * 1000
    });
    cleanupExpiredPending();
    return;
  }
  emittedSuggestionIds.add(key);
  eventBus.emit('stage:suggest', buildPayload(phone, suggestion));
}

function flushPendingForPhone(phone: string): void {
  cleanupExpiredPending();
  const pending = pendingStageSuggestions.get(phone);
  if (!pending) {
    return;
  }
  pendingStageSuggestions.delete(phone);
  emitOrPend(phone, pending.suggestion);
}

function cleanupExpiredPending(): void {
  const now = Date.now();
  pendingStageSuggestions.forEach((value, key) => {
    if (value.expiresAt < now) {
      pendingStageSuggestions.delete(key);
    }
  });
}

function buildPayload(phone: string, suggestion: ProfileSuggestion): StageSuggestPayload {
  return {
    phone,
    suggestionId: suggestion.id ?? suggestion.suggestionId,
    fromStage: String(suggestion.currentValue ?? suggestion.fromStage ?? '（未知）'),
    toStage: String(suggestion.suggestedValue ?? suggestion.toStage ?? ''),
    reason: suggestion.reason ?? 'AI 建议更新客户阶段',
    stageOptionMatch: suggestion.stageOptionMatch !== false,
    validOptions: suggestion.validOptions ?? [],
    createdAt: suggestion.createdAt,
    suggestionType: 'STAGE_CHANGE'
  };
}

function suggestionKey(phone: string, suggestion: ProfileSuggestion): string {
  return `${phone}:${String(suggestion.id ?? suggestion.suggestionId ?? suggestion.suggestedValue)}`;
}

async function refreshProfile(phone: string): Promise<void> {
  try {
    const response = await getJson<CustomerProfileView>(`/api/v1/customers/${encodeURIComponent(phone)}`);
    if (response.success && response.data) {
      customerProfileState.profile = response.data;
    }
  } catch {
    // Keep current card state when refresh fails.
  }
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => {
      retryTimers.delete(timer);
      resolve();
    }, ms);
    retryTimers.add(timer);
  });
}
