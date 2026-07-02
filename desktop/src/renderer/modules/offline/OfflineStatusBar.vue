<template>
  <div class="offline-stack" aria-live="polite">
    <div v-if="!isOnline" class="status-bar offline">
      <strong>离线模式</strong>
      <span>{{ offlineCopy }}</span>
    </div>
    <div v-else-if="hasWsDegraded" class="status-bar ws">
      <strong>提醒服务暂不可用</strong>
      <span>回复和搜索功能不受影响</span>
    </div>
    <div v-if="showOnlineToast" class="status-bar online">
      <strong>已恢复在线</strong>
      <span>正在静默同步最新数据</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import { hasWsDegraded, isOnline, offlineReason } from '../../shared/offlineManager';

const showOnlineToast = ref(false);
let disposeOnline: (() => void) | null = null;
let toastTimer: number | null = null;

const offlineCopy = computed(() => {
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
});

onMounted(() => {
  disposeOnline = eventBus.on('network:online', () => {
    showOnlineToast.value = true;
    if (toastTimer) {
      window.clearTimeout(toastTimer);
    }
    toastTimer = window.setTimeout(() => {
      showOnlineToast.value = false;
    }, loadDesktopConfig().onlineToastDurationMs);
  });
});

onBeforeUnmount(() => {
  disposeOnline?.();
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
});
</script>

<style scoped>
.offline-stack {
  position: sticky;
  top: 0;
  z-index: 50;
  display: grid;
  gap: 4px;
}

.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.12);
  font-size: 13px;
  line-height: 1.35;
}

.status-bar strong {
  flex: 0 0 auto;
}

.status-bar span {
  min-width: 0;
  text-align: right;
  color: rgba(15, 23, 42, 0.72);
}

.status-bar.offline {
  background: #fff4c2;
  color: #553600;
}

.status-bar.ws {
  background: #eef0f3;
  color: #374151;
}

.status-bar.online {
  background: #dff7e8;
  color: #14532d;
}
</style>
