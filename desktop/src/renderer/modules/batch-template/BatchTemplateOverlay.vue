<template>
  <section v-if="state.phase !== 'IDLE'" class="batch-overlay">
    <div v-if="state.phase === 'SELECT_TEMPLATE'" class="batch-workspace">
      <header class="batch-header">
        <div>
          <h2>批量发模板</h2>
          <p>{{ totalBatchCount }} 个客户 · {{ state.loadingCustomers ? '正在加载客户档案' : '客户档案已准备' }}</p>
        </div>
        <button class="secondary" @click="exitBatchTemplate">取消</button>
      </header>

      <p v-if="state.error" class="hint">{{ state.error }}</p>
      <p v-if="state.toast" class="toast">{{ state.toast }}</p>

      <nav class="batch-scene-tabs">
        <button
          v-for="scene in scenes"
          :key="scene"
          :class="{ active: state.filterScene === scene }"
          @click="setBatchSceneFilter(scene)"
        >
          {{ scene }}
        </button>
      </nav>

      <div v-if="state.loadingTemplates" class="loading-skeleton">
        <div class="skeleton-card"></div>
        <div class="skeleton-card"></div>
      </div>
      <div v-else class="batch-template-list">
        <button
          v-for="template in visibleBatchTemplates"
          :key="template.id"
          :class="['batch-template-row', { selected: state.selectedTemplateId === template.id }]"
          @click="selectBatchTemplate(template.id)"
        >
          <span>
            <strong>{{ template.title }}</strong>
            <em>{{ template.content.slice(0, 42) }}</em>
          </span>
          <small>{{ template.shortcutCode }}</small>
        </button>
      </div>

      <footer class="batch-footer">
        <button class="secondary" @click="exitBatchTemplate">取消</button>
        <button class="primary" :disabled="!selectedTemplate || state.loadingCustomers" @click="confirmBatchTemplate">
          确认使用此模板
        </button>
      </footer>
    </div>

    <div v-else-if="state.phase === 'PAUSED'" class="batch-paused">
      <strong>批量发送暂停中 · 第 {{ state.currentIndex + 1 }} / {{ totalBatchCount }} 个</strong>
      <button class="primary" @click="resumeBatchTemplate">继续</button>
      <button class="secondary" @click="exitBatchTemplate">退出批量</button>
    </div>

    <div v-else-if="state.phase === 'COMPLETED'" class="batch-complete">
      <h2>全部完成</h2>
      <p>共 {{ totalBatchCount }} 个客户</p>
      <p>已复制 {{ copiedBatchCount }} 个</p>
      <p v-if="remainingBatchCount">未处理 {{ remainingBatchCount }} 个</p>
      <button class="primary" @click="exitBatchTemplate">返回跟进清单</button>
    </div>

    <div v-else class="batch-workspace sending">
      <header class="batch-header">
        <div>
          <h2>批量发送 · {{ selectedTemplate?.title }}</h2>
          <p>第 {{ state.currentIndex + 1 }} / {{ totalBatchCount }} 个</p>
        </div>
        <select :value="state.selectedTemplateId ?? ''" @change="selectBatchTemplate(Number(($event.target as HTMLSelectElement).value))">
          <option v-for="template in state.templates" :key="template.id" :value="template.id">
            {{ template.title }}
          </option>
        </select>
      </header>

      <article v-if="currentBatchCustomer?.profile?.customer" class="batch-customer-card">
        <div class="batch-customer-head">
          <button class="link-button batch-name" @click="copyBatchCustomerField(currentBatchCustomer.profile.customer.nickname || '', '昵称')">
            {{ currentBatchCustomer.profile.customer.nickname || `客户 ${currentBatchCustomer.phone.slice(-4)}` }}
          </button>
          <button class="link-button" @click="copyBatchCustomerField(currentBatchCustomer.phone, '手机号')">
            {{ currentBatchCustomer.phone.slice(-4) }}
          </button>
        </div>
        <p>
          {{ leadTypeLabel(currentBatchCustomer.profile.customer.leadType) }} ·
          {{ formatDate(currentBatchCustomer.profile.customer.lastFollowupAt) }} ·
          {{ currentBatchCustomer.profile.customer.intendedStore || '-' }}
        </p>
        <pre class="batch-template-text">{{ filledTemplateText }}</pre>
      </article>

      <article v-else class="batch-customer-card unavailable">
        <strong>此客户数据暂不可用</strong>
        <p>手机号后四位 {{ currentBatchCustomer?.phone.slice(-4) || '-' }}，系统已跳过变量替换。</p>
      </article>

      <div class="batch-actions">
        <button class="secondary" :disabled="state.currentIndex === 0" @click="previousBatchCustomer">上一个</button>
        <button class="primary" :disabled="!filledTemplateText || currentBatchCustomer?.copied" @click="copyCurrentBatchText">
          {{ currentBatchCustomer?.copied ? '已复制' : '复制' }}
        </button>
        <button class="secondary" @click="nextBatchCustomer">下一个</button>
      </div>

      <footer class="batch-status">
        <div class="batch-progress"><span :style="{ width: `${batchProgressPercent}%` }"></span></div>
        <p>已处理 {{ processedBatchCount }} · 已复制 {{ copiedBatchCount }} · 未处理 {{ remainingBatchCount }}</p>
        <button class="secondary small" @click="pauseBatchTemplate">暂停</button>
        <button class="secondary small" @click="exitBatchTemplate">退出批量</button>
      </footer>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  batchProgressPercent,
  batchTemplateState as state,
  confirmBatchTemplate,
  copiedBatchCount,
  copyBatchCustomerField,
  copyCurrentBatchText,
  currentBatchCustomer,
  exitBatchTemplate,
  filledTemplateText,
  nextBatchCustomer,
  pauseBatchTemplate,
  previousBatchCustomer,
  processedBatchCount,
  remainingBatchCount,
  resumeBatchTemplate,
  selectBatchTemplate,
  selectedTemplate,
  setBatchSceneFilter,
  startBatchTemplateFlow,
  totalBatchCount,
  visibleBatchTemplates
} from './batchTemplateStore';
import type { BatchStartPayload } from './types';

const scenes = ['全部', '开场白', '催约', '唤醒', '预约提醒'];
let dispose: (() => void) | null = null;

onMounted(() => {
  dispose = eventBus.on<BatchStartPayload>('batch:start', (payload) => {
    void startBatchTemplateFlow(payload);
  });
});

onBeforeUnmount(() => {
  dispose?.();
});

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}

function formatDate(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
</script>
