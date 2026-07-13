import { computed } from 'vue';
import { desktopNoticeState } from '../../shared/desktopNoticeStore';
import { desktopStatusState } from '../../shared/desktopStatusStore';
import { hasWsDegraded, isOnline, offlineReason } from '../../shared/offlineManager';
import { visibleWorkbenchNotices } from '../workbench/workbenchStore';
import { alertStore } from './alertStore';

export type GlobalAlertLevel = 'ERROR' | 'WARN' | 'INFO';

export type GlobalAlertItem = {
  id: string;
  source: 'DESKTOP' | 'NETWORK' | 'ABNORMAL' | 'NOTICE' | 'SKILL' | 'LLM';
  title: string;
  message: string;
  level: GlobalAlertLevel;
  occurredAt?: string;
  abnormalAlertId?: string;
};

export const globalAlertItems = computed<GlobalAlertItem[]>(() => {
  const items: GlobalAlertItem[] = [];
  if (desktopNoticeState.message) {
    items.push({
      id: 'desktop-notice',
      source: 'DESKTOP',
      title: desktopNoticeState.kind === 'error' ? '桌面操作异常' : '桌面提醒',
      message: desktopNoticeState.message,
      level: desktopNoticeState.kind === 'error' ? 'ERROR' : 'INFO'
    });
  }
  if (!isOnline.value) {
    items.push({
      id: 'network-offline',
      source: 'NETWORK',
      title: '离线模式',
      message: offlineCopy(),
      level: 'ERROR'
    });
  } else if (hasWsDegraded.value) {
    items.push({
      id: 'network-ws',
      source: 'NETWORK',
      title: '提醒服务暂不可用',
      message: '实时提醒连接异常，回复和搜索功能不受影响',
      level: 'WARN'
    });
  }
  Array.from(alertStore.values()).flat()
    .filter((alert) => !alert.acknowledged)
    .forEach((alert) => {
      items.push({
        id: `abnormal-${alert.alertId}`,
        source: 'ABNORMAL',
        title: alert.alertType === 'CUSTOMER_COMPLAINT' ? '客户不满' : '流失风险',
        message: `${maskPhone(alert.phone)} · ${alert.message}`,
        level: alert.level,
        occurredAt: alert.occurredAt,
        abnormalAlertId: alert.alertId
      });
    });
  visibleWorkbenchNotices.value.forEach((notice) => {
    items.push({
      id: `notice-${notice.noticeId}`,
      source: 'NOTICE',
      title: notice.title,
      message: notice.content,
      level: notice.level,
      occurredAt: notice.createdAt
    });
  });
  if (desktopStatusState.skillStatus.status === 'EXPIRED' || desktopStatusState.skillStatus.status === 'EXPIRING') {
    items.push({
      id: 'skill-expiry',
      source: 'SKILL',
      title: desktopStatusState.skillStatus.status === 'EXPIRED' ? 'Skill 已到期' : 'Skill 即将到期',
      message: desktopStatusState.skillStatus.label,
      level: desktopStatusState.skillStatus.status === 'EXPIRED' ? 'ERROR' : 'WARN'
    });
  }
  if (desktopStatusState.llmStatus.status === 'WARN') {
    items.push({
      id: 'llm-status',
      source: 'LLM',
      title: 'LLM 配置需处理',
      message: desktopStatusState.llmStatus.detail || desktopStatusState.llmStatus.label,
      level: 'WARN'
    });
  }
  return items.sort(compareAlertPriority);
});

export const globalAlertCount = computed(() => globalAlertItems.value.length);
export const topGlobalAlert = computed(() => globalAlertItems.value[0] ?? null);

function compareAlertPriority(left: GlobalAlertItem, right: GlobalAlertItem): number {
  const priorityDiff = levelPriority(right.level) - levelPriority(left.level);
  if (priorityDiff !== 0) {
    return priorityDiff;
  }
  return (right.occurredAt ?? '').localeCompare(left.occurredAt ?? '');
}

function levelPriority(level: GlobalAlertLevel): number {
  if (level === 'ERROR') return 3;
  if (level === 'WARN') return 2;
  return 1;
}

function offlineCopy(): string {
  if (offlineReason.value === 'OS_OFFLINE') {
    return '系统网络不可用，已切换到本地缓存';
  }
  if (offlineReason.value === 'API_CONSECUTIVE_FAIL') {
    return '服务连续无法访问，已切换到本地缓存';
  }
  if (offlineReason.value === 'WS_AND_API_FAILED') {
    return '提醒和服务连接异常，已切换到本地缓存';
  }
  return '已切换到本地缓存';
}

function maskPhone(phone: string): string {
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : `****${phone.slice(-4)}`;
}
