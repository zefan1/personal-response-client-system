<template>
  <section v-if="state.visible" class="quick-search-overlay" @keydown.esc="hideQuickSearch">
    <div class="quick-search-box">
      <input
        ref="inputRef"
        class="quick-search-input"
        placeholder="搜索话术、项目介绍、FAQ..."
        @input="onInput"
        @keydown.enter.prevent="copySelected"
      />
      <nav class="quick-filter">
        <button v-for="filter in filters" :key="filter.value" :class="{ active: state.filter === filter.value }" @click="setQuickSearchFilter(filter.value)">
          {{ filter.label }}
        </button>
      </nav>
      <p v-if="state.offline" class="hint">离线模式，内容可能不是最新</p>
      <p v-if="state.error" class="hint">{{ state.error }} <button class="secondary small" @click="refreshQuickSearchItems">重试</button></p>
      <div v-if="state.loading && state.items.length === 0" class="loading-skeleton">
        <div class="skeleton-card"></div>
        <div class="skeleton-card"></div>
      </div>
      <div v-else-if="groupedQuickSearchItems.length" class="quick-results">
        <section v-for="group in groupedQuickSearchItems" :key="group.type">
          <h3>{{ contentTypeLabel(group.type) }}</h3>
          <button v-for="item in group.items" :key="item.id" class="quick-item" @click="copyQuickSearchItem(item)">
            <span>
              <strong>{{ item.title }}</strong>
              <em>{{ item.shortcutCode }} · {{ leadTypeLabel(item.leadType) }} · {{ item.scene || '-' }}</em>
            </span>
            <span>{{ item.contentType }}</span>
          </button>
        </section>
      </div>
      <p v-else class="empty-panel">暂无可用内容，请联系管理员配置</p>
      <p v-if="state.toast" class="toast">{{ state.toast }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { onQuickSearchHide, onQuickSearchShow } from '../../shared/desktopBridge';
import { eventBus } from '../../shared/eventBus';
import {
  cleanupQuickSearchStore,
  copyQuickSearchItem,
  filteredQuickSearchItems,
  groupedQuickSearchItems,
  handleQuickSearchConfigRefresh,
  handleQuickSearchOffline,
  handleQuickSearchOnline,
  hideQuickSearch,
  initializeQuickSearch,
  quickSearchState as state,
  refreshQuickSearchItems,
  scheduleQuickSearchQuery,
  setQuickSearchFilter,
  showQuickSearch
} from './quickSearchStore';
import type { QuickSearchContentType, QuickSearchFilter } from './types';

const inputRef = ref<HTMLInputElement | null>(null);
const filters: Array<{ value: QuickSearchFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'TUAN_GOU', label: '团购' },
  { value: 'XIAN_SUO', label: '线索' },
  { value: 'GENERAL', label: '通用' }
];
const disposers: Array<() => void> = [];

onMounted(() => {
  void initializeQuickSearch();
  disposers.push(onQuickSearchShow(() => {
    showQuickSearch();
    void nextTick(() => inputRef.value?.focus());
  }));
  disposers.push(onQuickSearchHide(hideQuickSearch));
  disposers.push(eventBus.on('CONFIG_REFRESH', handleQuickSearchConfigRefresh));
  disposers.push(eventBus.on('network:offline', handleQuickSearchOffline));
  disposers.push(eventBus.on('network:online', handleQuickSearchOnline));
  disposers.push(eventBus.on('quick-search:show', () => {
    showQuickSearch();
    void nextTick(() => inputRef.value?.focus());
  }));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupQuickSearchStore();
});

function onInput(event: Event) {
  scheduleQuickSearchQuery((event.target as HTMLInputElement).value);
}

function copySelected() {
  const item = filteredQuickSearchItems.value[state.selectedIndex] ?? filteredQuickSearchItems.value[0];
  if (item) {
    void copyQuickSearchItem(item);
  }
}

function contentTypeLabel(type: QuickSearchContentType): string {
  if (type === 'TEMPLATE') return '话术模板';
  if (type === 'KNOWLEDGE') return '知识片段';
  if (type === 'LOCATION') return '门店定位';
  if (type === 'IMAGE') return '图片素材';
  return '小程序引导';
}

function leadTypeLabel(value: string): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'GENERAL') return '通用';
  return value || '-';
}
</script>
