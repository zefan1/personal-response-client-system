<template>
  <section v-if="state.visible" class="quick-search-overlay" @click.self="hideQuickSearch" @keydown.esc="hideQuickSearch">
    <aside class="quick-search-box" aria-label="模板">
      <header class="quick-search-head">
        <div>
          <h2>模板</h2>
          <p>搜索并复制常用话术、知识片段和素材。</p>
        </div>
        <button class="icon-close-button" type="button" aria-label="关闭模板" title="关闭模板" @click="hideQuickSearch">
          <span aria-hidden="true">×</span>
        </button>
      </header>
      <input
        ref="inputRef"
        class="quick-search-input"
        placeholder="搜索标题、内容或模板码"
        @input="onInput"
        @keydown.down.prevent="moveQuickSearchSelection(1)"
        @keydown.up.prevent="moveQuickSearchSelection(-1)"
        @keydown.enter.prevent="copySelected"
      />
      <nav class="quick-filter" aria-label="模板筛选">
        <button v-for="filter in filters" :key="filter.value" :class="{ active: state.filter === filter.value }" type="button" @click="setQuickSearchFilter(filter.value)">
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
          <article
            v-for="item in group.items"
            :key="item.id"
            :class="['quick-item', { selected: selectedItemId === item.id }]"
            @mouseenter="selectQuickSearchItem(item)"
          >
            <img v-if="item.contentType === 'IMAGE' && item.imageUrl" class="quick-item-thumb" :src="item.imageUrl" :alt="item.title" />
            <div class="quick-item-copy">
              <strong>{{ item.title }}</strong>
              <em>{{ contentTypeLabel(item.contentType) }} · {{ leadTypeLabel(item.leadType) }} · {{ item.scene || item.shortcutCode || '-' }}</em>
              <p>{{ item.content }}</p>
            </div>
            <button class="primary small" type="button" @click="copyQuickSearchItem(item)">复制</button>
          </article>
        </section>
      </div>
      <p v-else class="empty-panel">{{ emptyText }}</p>
      <p v-if="state.toast" class="toast">{{ state.toast }}</p>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
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
  moveQuickSearchSelection,
  quickSearchState as state,
  refreshQuickSearchItems,
  scheduleQuickSearchQuery,
  selectQuickSearchItem,
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
const selectedItemId = computed(() => filteredQuickSearchItems.value[state.selectedIndex]?.id);
const emptyText = computed(() => {
  if (state.items.length === 0) {
    return '暂无可用内容，请联系管理员配置';
  }
  if (state.query.trim()) {
    return '没有匹配的内容，请换个关键词试试';
  }
  return '当前分类暂无内容，请切换筛选条件';
});

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
  if (type === 'MINI_PROGRAM') return '小程序引导';
  return '小程序引导';
}

function leadTypeLabel(value: string): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'GENERAL') return '通用';
  return value || '-';
}
</script>
