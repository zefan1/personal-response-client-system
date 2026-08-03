<template>
  <section class="communication-history-panel">
    <header class="panel-header communication-history-header">
      <div>
        <h2>{{ customerName }}的沟通记录</h2>
        <p>{{ maskPhone(state.phone) }}</p>
      </div>
      <button class="secondary small communication-return-profile" type="button" @click="returnToProfile">
        返回客户档案
      </button>
    </header>

    <div class="communication-view-tabs" role="tablist" aria-label="沟通记录视图">
      <button
        type="button"
        :class="{ active: state.activeView === 'messages' }"
        @click="switchCommunicationView('messages')"
      >聊天记录</button>
      <button
        type="button"
        :class="{ active: state.activeView === 'summaries' }"
        @click="switchCommunicationView('summaries')"
      >历史汇总</button>
    </div>

    <template v-if="state.activeView === 'messages'">
      <form class="communication-filters" @submit.prevent="applyCommunicationFilters">
        <label>
          <span>平台</span>
          <select v-model="state.platform">
            <option value="">全部</option>
            <option value="WECHAT">微信</option>
            <option value="WECOM">企业微信</option>
            <option value="DOUYIN">抖音</option>
            <option value="XIAOHONGSHU">小红书</option>
            <option value="MEITUAN">美团</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label><span>开始日期</span><input v-model="state.fromDate" type="date" /></label>
        <label><span>结束日期</span><input v-model="state.toDate" type="date" /></label>
        <label class="communication-keyword"><span>关键词</span><input v-model="state.keyword" type="search" /></label>
        <button class="primary small" type="submit">筛选</button>
      </form>

      <div class="communication-message-list">
        <button
          v-if="state.nextBeforeId !== null"
          class="secondary small communication-load-older"
          type="button"
          :disabled="state.loadingOlder"
          @click="loadOlderMessages"
        >{{ state.loadingOlder ? '加载中...' : '加载更早记录' }}</button>
        <section v-for="group in groupedMessages" :key="group.date" class="communication-date-group">
          <h3>{{ group.date }}</h3>
          <article
            v-for="message in group.messages"
            :key="message.id"
            :class="['communication-message', message.senderRole === 'EMPLOYEE' ? 'from-employee' : 'from-customer']"
          >
            <div class="communication-message-meta">
              <strong>{{ senderLabel(message.senderRole) }}</strong>
              <span>{{ platformLabel(message.platformCode) }}</span>
              <span>{{ timeLabel(message) }}</span>
              <span v-if="isCorrected(message)" class="communication-corrected-marker">已修正</span>
            </div>
            <p>{{ message.currentText }}</p>
            <details v-if="isCorrected(message)">
              <summary>查看原识别文字</summary>
              <p>原识别文字：{{ message.originalText }}</p>
            </details>
            <button class="communication-correct-action" type="button" @click="correctMessage(message)">修正文字</button>
          </article>
        </section>
        <p v-if="!state.loading && !state.messages.length" class="empty-panel">暂无聊天记录</p>
      </div>
    </template>

    <div v-else class="communication-summary-history-list">
      <article
        v-for="summary in state.summaries"
        :key="summary.id"
        class="communication-summary-version"
      >
        <div><strong>第 {{ summary.versionNo }} 版</strong><span>{{ formatDateTime(summary.generatedAt) }}</span></div>
        <p>{{ summary.summaryText }}</p>
      </article>
      <p v-if="!state.loading && !state.summaries.length" class="empty-panel">暂无历史汇总</p>
    </div>

    <p v-if="state.loading" class="empty-panel">加载中...</p>
    <p v-if="state.error" class="toast">{{ state.error }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, watch } from 'vue';
import { eventBus } from '../../shared/eventBus';
import { customerProfileState } from '../customer-profile/customerProfileStore';
import {
  applyCommunicationFilters,
  communicationHistoryState as state,
  correctCommunicationMessage,
  loadOlderMessages,
  switchCommunicationView
} from './communicationHistoryStore';
import type { ArchivedCommunicationMessage } from './types';

const customerName = computed(() => {
  const profile = customerProfileState.profile;
  const profilePhone = profile?.phoneFull || profile?.customer.phoneFull || profile?.customer.phone;
  return profilePhone === state.phone ? profile?.customer.nickname || '客户' : '客户';
});

const groupedMessages = computed(() => {
  const groups = new Map<string, ArchivedCommunicationMessage[]>();
  state.messages.forEach((message) => {
    const date = message.messageTime?.slice(0, 10) || '未知日期';
    groups.set(date, [...(groups.get(date) ?? []), message]);
  });
  return [...groups.entries()].map(([date, messages]) => ({ date, messages }));
});

watch(() => state.messages.length, async () => {
  await nextTick();
  document.querySelector('.communication-message-list')?.scrollTo({ top: 999999 });
});

function returnToProfile(): void {
  eventBus.emit('communication:return-profile', { phone: state.phone, customerId: state.customerId });
}

function senderLabel(role: string): string {
  return role === 'EMPLOYEE' ? '员工' : '客户';
}

function platformLabel(platform: string): string {
  return {
    WECHAT: '微信',
    WECOM: '企业微信',
    DOUYIN: '抖音',
    XIAOHONGSHU: '小红书',
    MEITUAN: '美团',
    OTHER: '其他'
  }[platform] || platform || '其他';
}

function timeLabel(message: ArchivedCommunicationMessage): string {
  const time = message.messageTime?.slice(11, 16) || '--:--';
  return message.timeEstimated ? `识别于 ${time}` : time;
}

function isCorrected(message: ArchivedCommunicationMessage): boolean {
  return message.originalText !== message.currentText;
}

async function correctMessage(message: ArchivedCommunicationMessage): Promise<void> {
  const corrected = window.prompt('修正识别文字', message.currentText);
  if (corrected !== null) {
    await correctCommunicationMessage(message.id, corrected);
  }
}

function maskPhone(phone: string): string {
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone;
}

function formatDateTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
</script>
