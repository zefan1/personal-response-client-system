import { reactive } from 'vue';
import { getJson } from './apiClient';
import { saveDesktopConfig } from './config';

export type DesktopSkillStatus = 'OK' | 'EXPIRING' | 'EXPIRED' | 'UNKNOWN';
export type DesktopLlmStatus = 'OK' | 'WARN' | 'UNKNOWN';
export type DesktopRole = 'ADMIN' | 'LEADER' | 'KEEPER' | '';

export type DesktopStatusPayload = {
  accountName?: string;
  role?: DesktopRole;
  skillStatus?: {
    status?: DesktopSkillStatus;
    expireAt?: string | null;
    daysLeft?: number | null;
    label?: string;
  };
  llmStatus?: {
    status?: DesktopLlmStatus;
    label?: string;
    detail?: string;
    replyGenerationEnabled?: boolean;
  };
  runtimeConfig?: {
    clipboardScreenshotConfirmPromptS?: number | null;
  };
};

export const desktopStatusState = reactive({
  loading: false,
  error: '',
  accountName: '',
  role: '' as DesktopRole,
  skillStatus: {
    status: 'UNKNOWN' as DesktopSkillStatus,
    expireAt: null as string | null,
    daysLeft: null as number | null,
    label: '技能有效期未配置'
  },
  llmStatus: {
    status: 'UNKNOWN' as DesktopLlmStatus,
    label: 'LLM 状态未加载',
    detail: '',
    replyGenerationEnabled: false
  },
  runtimeConfig: {
    clipboardScreenshotConfirmPromptS: 10
  }
});

export async function loadDesktopStatus(): Promise<void> {
  if (desktopStatusState.loading) {
    return;
  }
  desktopStatusState.loading = true;
  desktopStatusState.error = '';
  try {
    const response = await getJson<DesktopStatusPayload>('/api/v1/desktop/status');
    if (!response.success || !response.data) {
      throw new Error(response.message ?? 'desktop status fetch failed');
    }
    applyDesktopStatus(response.data);
  } catch (error) {
    desktopStatusState.error = error instanceof Error ? error.message : '桌面状态加载失败';
  } finally {
    desktopStatusState.loading = false;
  }
}

export function applyDesktopStatus(payload: DesktopStatusPayload): void {
  desktopStatusState.accountName = payload.accountName?.trim() ?? desktopStatusState.accountName;
  desktopStatusState.role = normalizeRole(payload.role) || desktopStatusState.role;
  desktopStatusState.skillStatus.status = normalizeSkillStatus(payload.skillStatus?.status);
  desktopStatusState.skillStatus.expireAt = payload.skillStatus?.expireAt ?? null;
  desktopStatusState.skillStatus.daysLeft = Number.isFinite(payload.skillStatus?.daysLeft)
    ? Number(payload.skillStatus?.daysLeft)
    : null;
  desktopStatusState.skillStatus.label = payload.skillStatus?.label?.trim() || '技能有效期未配置';
  desktopStatusState.llmStatus.status = normalizeLlmStatus(payload.llmStatus?.status);
  desktopStatusState.llmStatus.label = payload.llmStatus?.label?.trim() || 'LLM 状态未加载';
  desktopStatusState.llmStatus.detail = payload.llmStatus?.detail?.trim() || '';
  desktopStatusState.llmStatus.replyGenerationEnabled = payload.llmStatus?.replyGenerationEnabled === true;

  const clipboardPromptSeconds = normalizeClipboardPromptSeconds(payload.runtimeConfig?.clipboardScreenshotConfirmPromptS);
  desktopStatusState.runtimeConfig.clipboardScreenshotConfirmPromptS = clipboardPromptSeconds;
  saveDesktopConfig({ clipboardScreenshotConfirmPromptS: clipboardPromptSeconds });
}

export function resetDesktopStatus(): void {
  desktopStatusState.loading = false;
  desktopStatusState.error = '';
  desktopStatusState.accountName = '';
  desktopStatusState.role = '';
  desktopStatusState.skillStatus.status = 'UNKNOWN';
  desktopStatusState.skillStatus.expireAt = null;
  desktopStatusState.skillStatus.daysLeft = null;
  desktopStatusState.skillStatus.label = '技能有效期未配置';
  desktopStatusState.llmStatus.status = 'UNKNOWN';
  desktopStatusState.llmStatus.label = 'LLM 状态未加载';
  desktopStatusState.llmStatus.detail = '';
  desktopStatusState.llmStatus.replyGenerationEnabled = false;
  desktopStatusState.runtimeConfig.clipboardScreenshotConfirmPromptS = 10;
}

function normalizeRole(value?: string): DesktopRole {
  if (value === 'ADMIN' || value === 'LEADER' || value === 'KEEPER') {
    return value;
  }
  return '';
}

function normalizeSkillStatus(value?: string): DesktopSkillStatus {
  if (value === 'OK' || value === 'EXPIRING' || value === 'EXPIRED' || value === 'UNKNOWN') {
    return value;
  }
  return 'UNKNOWN';
}

function normalizeLlmStatus(value?: string): DesktopLlmStatus {
  if (value === 'OK' || value === 'WARN' || value === 'UNKNOWN') {
    return value;
  }
  return 'UNKNOWN';
}

function normalizeClipboardPromptSeconds(value?: number | null): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return 10;
  }
  const integer = Math.trunc(parsed);
  if (integer === 0 || (integer >= 3 && integer <= 60)) {
    return integer;
  }
  return 10;
}
