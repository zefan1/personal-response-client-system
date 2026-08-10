<template>
  <section :class="['customer-panel', { 'customer-panel-embedded': embedded }]">
    <header v-if="!embedded" class="panel-header">
      <div>
        <h2>客户档案</h2>
        <p>{{ state.profile ? summaryText : '搜索或从候选列表选择客户' }}</p>
      </div>
      <button
        class="secondary small icon-refresh-button"
        type="button"
        :disabled="!state.profile || state.profileLoading"
        aria-label="刷新客户档案"
        title="刷新"
        @click="refreshCurrent"
      >
        ↻
      </button>
    </header>

    <div v-if="!embedded" class="search-row">
      <input
        v-model="state.keyword"
        placeholder="粘贴手机号或昵称搜索"
        @input="onInput"
        @paste="onPaste"
      />
      <button class="primary" :disabled="state.searchLoading" @click="searchImmediately(state.keyword)">
        {{ state.searchLoading ? '搜索中...' : '搜索' }}
      </button>
    </div>

    <div v-if="!embedded && state.searchMessage" class="empty-panel customer-search-state">
      <strong>{{ state.searchMessage }}</strong>
      <p v-if="!state.searchLoading">可换手机号后四位或昵称再试</p>
    </div>
    <div v-if="!embedded && state.searchResults.length" class="search-results">
      <button v-for="customer in state.searchResults" :key="customer.customerId || customer.phoneFull || customer.phone" class="result-row" @click="openProfile(customer.customerId || customer.phoneFull || customer.phone, 'SEARCH')">
        <span class="result-identity">
          <strong class="result-nickname">{{ customer.nickname || '-' }}</strong>
          <span class="result-phone">{{ maskPhone(customer.phone) }}</span>
        </span>
        <span class="result-meta">
          <span>{{ customer.sourceChannel || '-' }}</span>
          <span>{{ formatSearchDate(customer.lastFollowupAt) }}</span>
          <span>{{ customer.intendedStore || '-' }}</span>
        </span>
      </button>
      <p v-if="state.searchTruncated" class="hint">还有更多结果，建议用手机号精确搜索</p>
    </div>

    <p v-if="!embedded && (state.offline || state.fromCache)" class="banner">
      {{ state.offline ? '离线数据' : '缓存数据' }}，上次缓存于 {{ formatDate(state.cachedAt) }}
    </p>
    <p v-if="!embedded && state.pendingSaveBanner" class="banner">{{ state.pendingSaveBanner }}</p>
    <div v-if="!embedded && state.tableSyncStatus" :class="['profile-table-sync-status', `level-${state.tableSyncStatus.level}`]">
      <strong>{{ state.tableSyncStatus.message }}</strong>
      <span v-if="state.tableSyncStatus.detail">{{ state.tableSyncStatus.detail }}</span>
    </div>
    <p v-if="!embedded && state.profileAlert" :class="['profile-alert-banner', `level-${state.profileAlert.level.toLowerCase()}`]">
      {{ state.profileAlert.message }}
    </p>

    <article v-if="state.profile" class="profile-card">
      <p v-if="state.editMode" class="profile-edit-banner">正在编辑档案，保存后会自动刷新最新资料。</p>
      <section class="profile-overview" aria-label="客户档案第一眼总览">
      <div class="profile-summary profile-identity-summary">
        <div>
          <template v-if="state.editMode && !readOnly">
            <label class="profile-nickname-editor">
              微信昵称
              <input v-model="state.editFields.nickname" placeholder="添加好友后看到的微信昵称" />
            </label>
          </template>
          <strong v-else>{{ customer.nickname || '-' }}</strong>
          <button
            v-if="!readOnly"
            class="secondary profile-copy-nickname"
            type="button"
            aria-label="复制客户昵称"
            title="复制客户昵称"
            @click="copyCustomerNickname"
          ><span aria-hidden="true">⧉</span></button>
          <span v-if="customer.customerStage" class="profile-stage">{{ customer.customerStage }}</span>
          <p class="profile-identity-meta">
            {{ maskPhone(customer.phone) }} · 微信{{ customer.nickname ? '已关联' : '待关联' }} · 分配管家：{{ customer.assignedKeeper || '-' }} · 来源：{{ customer.sourceChannel || leadTypeLabel(customer.leadType) }}
          </p>
        </div>
        <div v-if="!readOnly" class="profile-actions">
          <button class="primary small" :disabled="state.generating || !customer.phone" @click="generateReplyFromProfile">
            {{ state.generating ? '生成中...' : '生成回复' }}
          </button>
          <button v-if="!state.editMode" class="secondary small" @click="enterEditMode">编辑档案</button>
          <template v-else>
            <button class="primary small" :disabled="state.saving" @click="saveProfileEdits">{{ state.saving ? '保存中...' : '保存' }}</button>
            <button class="secondary small" @click="cancelEditMode">取消</button>
          </template>
        </div>
      </div>

      <p class="profile-overview-label">第一眼总览</p>
      <div class="profile-overview-grid">
        <section class="profile-overview-cell">
          <span>客户现在想要什么</span>
          <strong>{{ joinValues(customer.intendedProject, customer.intendedStore, customer.intentLevel) }}</strong>
          <p>已购：{{ customer.purchasedProject || '暂无' }} · 担忧：{{ customer.worries || customer.bodyConcerns || '暂无' }}</p>
        </section>
        <section class="profile-overview-cell">
          <span>最近到店安排</span>
          <strong>{{ appointmentOverview }}</strong>
          <p>{{ customer.appointmentStore || '门店待确认' }} · {{ customer.appointmentItem || customer.intendedProject || '项目待确认' }}<br>{{ customer.appointmentStatus || '未预约' }} · {{ customer.arrived || '待到店' }}</p>
        </section>
        <section class="profile-overview-cell">
          <span>下一步要做什么</span>
          <strong>{{ customer.nextFollowupDir || '暂无待办' }}</strong>
          <p>负责人：{{ customer.assignedKeeper || '-' }}<br>{{ formatDate(customer.nextFollowupAt) }}</p>
        </section>
      </div>

      <div class="profile-summary-grid">
        <section class="profile-quick-summary">
          <strong>客户快速了解</strong>
          <p>{{ quickProfileSummary }}</p>
        </section>
        <section class="profile-reminder-summary">
          <strong>最近沟通与提醒</strong>
          <p>{{ state.profile.latestCommunicationSummary?.summaryText || customer.customerProfileSummary || customer.followupNotes || '暂无沟通汇总' }}</p>
        </section>
      </div>
      </section>

      <p class="profile-overview-label profile-detail-label">完整档案</p>
      <details class="profile-detail-module communication-summary-panel" open aria-label="最新客户沟通汇总">
        <summary>
          <span>最新沟通汇总</span>
          <small>更新时间：{{ formatDate(state.profile.latestCommunicationSummary?.generatedAt) }}</small>
        </summary>
        <div class="communication-summary-body">
          <div class="communication-summary-actions">
            <button class="secondary small communication-summary-history" type="button" @click="openCommunication('summaries')">查看历史汇总</button>
            <button class="secondary small communication-message-history" type="button" @click="openCommunication('messages')">查看聊天记录</button>
          </div>
          <p>{{ state.profile.latestCommunicationSummary?.summaryText || customer.customerProfileSummary || '暂无沟通汇总' }}</p>
        </div>
      </details>

      <details class="profile-detail-module profile-detail-module-identity">
        <summary>
          <span>身份与归属</span>
          <small>客户身份、来源与归属信息</small>
        </summary>
        <FieldGrid v-if="state.editMode" :items="identityItems" />
        <ProfileFieldRows v-else :items="identitySummaryItems" />
      </details>
      <details class="profile-detail-module profile-detail-module-intent">
        <summary>
          <span>意向与购买</span>
          <small>当前有效资料</small>
        </summary>
        <FieldGrid v-if="state.editMode" :items="[...intentItems, ...conversionItems]" />
        <ProfileFieldRows v-else :items="intentSummaryItems" />
      </details>

      <details class="profile-detail-module profile-detail-module-body">
        <summary><span>身体情况与沟通偏好</span><small>已填写资料</small></summary>
        <FieldGrid v-if="state.editMode" :items="bodyItems" />
        <ProfileFieldRows v-else :items="bodySummaryItems" />
      </details>

      <details class="profile-detail-module profile-detail-module-followup profile-followup-module">
        <summary>
          <span>跟进历史、客户标签与 AI 建议</span>
          <small>持续更新，不覆盖旧记录</small>
        </summary>
        <div class="profile-history-entry">
          <time>{{ formatDate(customer.lastFollowupAt) }}</time>
          <p>{{ customer.followupNotes || '暂无跟进记录' }}</p>
        </div>
        <FieldGrid v-if="state.editMode" :items="followupItems" />
        <ProfileFieldRows v-else :items="followupSummaryItems" />
        <div v-if="(state.profile?.currentTags ?? []).length" class="profile-tag-badges" aria-label="客户标签">
          <span v-for="tag in state.profile?.currentTags ?? []" :key="tag.assignmentId">{{ tag.tagDisplayName || tag.tagValue }}</span>
        </div>
        <details v-if="(state.profile?.editableTagCategories ?? []).length" class="profile-tag-management">
          <summary>管理客户标签</summary>
        <div v-if="(state.profile?.editableTagCategories ?? []).length" class="tag-category-list">
          <article v-for="category in state.profile?.editableTagCategories ?? []" :key="category.id" class="tag-category-item">
            <div class="tag-category-header">
              <div>
                <strong>{{ category.categoryName }}</strong>
                <span class="tag-category-key">{{ category.categoryKey }}</span>
                <span v-if="isCategoryLocked(category.id)" class="tag-lock-state">已锁定</span>
              </div>
              <div v-if="!readOnly" class="tag-category-actions">
                <button
                  class="secondary small"
                  type="button"
                  :disabled="state.tagSavingCategoryId === category.id"
                  @click="toggleTagEdit(category)"
                >{{ state.tagEditingCategoryId === category.id ? '取消' : '编辑' }}</button>
                <button
                  class="secondary small"
                  type="button"
                  :disabled="state.tagSavingCategoryId === category.id"
                  @click="toggleCategoryLock(category.id)"
                >{{ isCategoryLocked(category.id) ? '解除锁定' : '锁定分类' }}</button>
              </div>
            </div>
            <p class="tag-current-values">{{ currentTagNames(category.id) || '未设置' }}</p>
            <div v-if="!readOnly && state.tagEditingCategoryId === category.id" class="tag-editor">
              <select
                :multiple="category.selectionMode === 'MULTI'"
                :value="draftSelection(category)"
                @change="onTagSelectionChange(category, $event)"
              >
                <option v-if="category.selectionMode === 'SINGLE'" :value="0">移除当前标签</option>
                <option
                  v-for="value in category.values.filter((item) => item.isEnabled && item.manualSelectable)"
                  :key="value.id"
                  :value="value.id"
                >{{ value.displayName || value.tagValue }}</option>
              </select>
              <div class="tag-editor-actions">
                <button
                  class="primary small"
                  type="button"
                  :disabled="state.tagSavingCategoryId === category.id"
                  @click="saveCustomerTags(category.id, state.tagDrafts[category.id] ?? [], '员工在客户档案中修改')"
                >保存标签</button>
                <button class="secondary small" type="button" @click="cancelTagEdit">取消</button>
              </div>
            </div>
          </article>
        </div>
        </details>
        <p v-else-if="!(state.profile?.currentTags ?? []).length" class="empty-panel">暂无客户标签</p>
        <div v-if="state.suggestions.length" class="suggestion-list">
          <article
            v-for="suggestion in state.suggestions"
            :key="suggestionKey(suggestion)"
            :class="['suggestion-item', { 'stage-change': suggestion.suggestionType === 'STAGE_CHANGE' || suggestion.fieldName === 'customerStage' }]"
          >
            <div>
              <strong>
                {{ suggestion.fieldName }}
                <span v-if="suggestion.suggestionType === 'STAGE_CHANGE' || suggestion.fieldName === 'customerStage'" class="stage-label">阶段建议</span>
              </strong>
              <p>{{ formatValue(suggestion.currentValue) }} → {{ formatValue(suggestion.suggestedValue) }}</p>
              <p class="reason">{{ suggestion.reason || suggestion.confidence || 'AI 建议更新该字段' }}</p>
              <p v-if="suggestion.stageOptionMatch === false" class="stage-warning">
                此阶段值不在表格当前可选范围内，请手动核对后再确认。
                表格当前可选阶段：{{ suggestion.validOptions?.join('、') || '-' }}
              </p>
            </div>
            <div v-if="!readOnly" class="suggestion-actions">
              <button class="secondary small" :disabled="suggestion.resolving || state.editMode" @click="resolveProfileSuggestion('CONFIRM', suggestion)">同意并执行</button>
              <button class="secondary small" :disabled="suggestion.resolving || state.editMode" @click="resolveProfileSuggestion('REJECT', suggestion)">拒绝</button>
            </div>
          </article>
          <div v-if="!readOnly" class="reply-actions">
            <button class="secondary small" :disabled="state.editMode" @click="resolveProfileSuggestion('CONFIRM')">全部同意并执行</button>
            <button class="secondary small" :disabled="state.editMode" @click="resolveProfileSuggestion('REJECT')">全部拒绝</button>
          </div>
        </div>
        <p v-else class="empty-panel">暂无建议</p>
      </details>

      <details class="profile-detail-module profile-detail-module-appointment profile-appointment-module">
        <summary>
          <span>预约、到店与服务资料</span>
          <small>向下预览历次记录</small>
        </summary>
        <details class="profile-detail-disclosure">
          <summary><span>最近一次预约</span><small>{{ appointmentOverview }} · {{ customer.appointmentStore || '门店待确认' }}</small></summary>
          <FieldGrid v-if="state.editMode" :items="appointmentItems" />
          <ProfileFieldRows v-else :items="appointmentSummaryItems" />
        <button v-if="!readOnly" class="primary small" type="button" :disabled="state.editMode" @click="beginBooking">确认预约并生成填写信息</button>
        <form v-if="!readOnly && state.bookingOpen" class="booking-form" @submit.prevent="confirmBooking">
          <label>预约日期<input v-model="state.bookingDraft.appointmentDate" type="date" required /></label>
          <label>预约时间<input v-model="state.bookingDraft.appointmentTime" type="time" /></label>
          <label>预约门店<input v-model="state.bookingDraft.appointmentStore" required /></label>
          <label>预约项目<input v-model="state.bookingDraft.appointmentItem" /></label>
          <button class="primary small" type="submit">生成预约信息</button>
          <textarea v-if="state.bookingTemplate" :value="state.bookingTemplate" readonly rows="6" aria-label="预约信息模板" />
        </form>
        </details>
        <details class="profile-detail-disclosure profile-appointment-history">
          <summary><span>后续到店记录</span><small>每次预约、到店与服务资料会继续追加</small></summary>
          <p>后续每一次预约、到店、接待和服务记录都会在这里继续向下查看。</p>
        </details>
      </details>
    </article>

    <div v-if="!embedded && state.tableSyncPrompt" class="toast table-sync-toast profile-sync-toast">
      <span>{{ state.toast }}</span>
      <button class="primary small" @click="confirmTableSync">同步</button>
      <button class="secondary small" @click="skipTableSync">暂不</button>
    </div>
    <p v-else-if="!embedded && state.toast" class="toast">{{ state.toast }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, watch, type PropType } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  appendProfileSuggestions,
  appendStageSuggestion,
  beginBooking,
  beginTagEdit,
  cancelTagEdit,
  cancelEditMode,
  cleanupCustomerProfileStore,
  confirmBooking,
  confirmTableSync,
  copyCustomerNickname,
  customerProfileState as state,
  enterEditMode,
  generateReplyFromProfile,
  handleCustomerTagsUpdated,
  handleProfileAbnormalAlert,
  handleSendConfirmed,
  handleStageUpdated,
  openProfile,
  currentProfileCustomerId,
  resolveProfileSuggestion,
  saveCustomerTags,
  saveProfileEdits,
  scheduleSearch,
  searchImmediately,
  skipTableSync,
  updateCustomerTagLock
} from './customerProfileStore';
import type {
  AbnormalAlertPayload,
  Customer,
  CustomerTagCategory,
  CustomerTagsUpdatedPayload,
  ProfileSuggestion,
  SourceFrom,
  StageSuggestPayload
} from './types';

const props = withDefaults(defineProps<{
  readOnly?: boolean;
  embedded?: boolean;
  customerId?: number | null;
}>(), {
  readOnly: false,
  embedded: false,
  customerId: null
});

const readOnly = computed(() => props.readOnly);
const embedded = computed(() => props.embedded);
const customer = computed(() => state.profile?.customer ?? {} as Customer);
const summaryText = computed(() => `${customer.value.nickname || '-'} · ${maskPhone(customer.value.phone || '')} · ${customer.value.customerStage || '-'}`);
const appointmentOverview = computed(() => {
  const date = formatDate(customer.value.appointmentDate);
  const time = customer.value.appointmentTime || '';
  return date === '-' ? '暂无到店安排' : `${date}${time ? ` ${time}` : ''}`;
});
const quickProfileSummary = computed(() => {
  if (customer.value.customerProfileSummary) {
    return customer.value.customerProfileSummary;
  }
  const postpartum = customer.value.postpartumMonths ? `产后 ${customer.value.postpartumMonths} 个月` : '';
  const delivery = customer.value.deliveryMethod || '';
  const concerns = customer.value.bodyConcerns || customer.value.worries || '';
  return joinValues(postpartum, delivery, concerns) === '-' ? '暂无足够资料，请在后续沟通中补充。' : joinValues(postpartum, delivery, concerns);
});

const intentItems = computed<Array<[string, unknown]>>(() => [
  ['来源渠道', editField('sourceChannel')],
  ['意向门店', editField('intendedStore')],
  ['意向项目', editField('intendedProject')],
  ['已购项目', editField('purchasedProject')],
  ['意向度', editField('intentLevel')],
  ['担忧点', editField('worries')]
]);

const intentSummaryItems = computed<Array<[string, string]>>(() => [
  ['来源、留资类型', joinValues(customer.value.sourceChannel, leadTypeLabel(customer.value.leadType))],
  ['意向门店、项目', joinValues(customer.value.intendedStore, customer.value.intendedProject)],
  ['已购项目、意向等级', joinValues(customer.value.purchasedProject, customer.value.intentLevel)],
  ['担忧点', customer.value.worries || customer.value.bodyConcerns || '-'],
  ['分配管家、日期', joinValues(customer.value.assignedKeeper, formatDate(customer.value.createdAt))],
  ['最近留资与转化追溯', joinValues(customer.value.thirdTrackingCapture, customer.value.secondTrackingCapture, customer.value.firstTrackingCapture)]
]);

const identityItems = computed<Array<[string, unknown]>>(() => [
  ['微信昵称', editField('nickname')],
  ['手机号', customer.value.phoneFull || customer.value.phone || '-'],
  ['当前阶段', editField('customerStage')],
  ['归属员工', editField('assignedKeeper')],
  ['线索类型', leadTypeLabel(customer.value.leadType)],
  ['性格', editField('personalityType')],
  ['来源渠道', editField('sourceChannel')],
  ['来源表', customer.value.sourceTable || '-'],
  ['来源行', customer.value.sourceRowId || '-'],
  ['留资时间', formatDate(customer.value.createdAt)]
]);

const identitySummaryItems = computed<Array<[string, string]>>(() => [
  ['微信昵称', customer.value.nickname || '-'],
  ['手机号', customer.value.phoneFull || customer.value.phone || '-'],
  ['当前阶段', customer.value.customerStage || '-'],
  ['归属员工', customer.value.assignedKeeper || '-'],
  ['线索类型', leadTypeLabel(customer.value.leadType)],
  ['性格', customer.value.personalityType || '-'],
  ['来源渠道', customer.value.sourceChannel || '-'],
  ['来源表', customer.value.sourceTable || '-'],
  ['来源行', customer.value.sourceRowId || '-'],
  ['留资时间', formatDate(customer.value.createdAt)]
]);

const bodyItems = computed<Array<[string, unknown]>>(() => [
  ['产后月份', editField('postpartumMonths')],
  ['胎次', editField('parity')],
  ['分娩方式', editField('deliveryMethod')],
  ['母乳状态', editField('breastfeeding')],
  ['恶露/月经', editField('lochiaPeriod')],
  ['孕期体重', editField('pregnancyWeight')],
  ['当前体重', editField('currentWeight')],
  ['身体关注', editField('bodyConcerns')],
  ['腹直肌', editField('diastasisRecti')],
  ['漏尿', editField('urineLeakage')],
  ['腰痛/耻骨痛', editField('pubicLumbago')],
  ['修复经历', editField('prevRepairExp')],
  ['产后检查', editField('postpartumCheck')],
  ['运动习惯', editField('exerciseHabits')]
]);

const bodySummaryItems = computed<Array<[string, string]>>(() => [
  ['性格与沟通偏好', customer.value.personalityType || '-'],
  ['产后情况', joinValues(customer.value.postpartumMonths ? `产后 ${customer.value.postpartumMonths} 个月` : '', customer.value.parity, customer.value.deliveryMethod, customer.value.breastfeeding)],
  ['身体关注', customer.value.bodyConcerns || '-'],
  ['恶露/月经', customer.value.lochiaPeriod || '-'],
  ['体重、腹直肌、漏尿、腰痛/耻骨痛', joinValues(weightText('孕期', customer.value.pregnancyWeight), weightText('当前', customer.value.currentWeight), customer.value.diastasisRecti ? `腹直肌：${customer.value.diastasisRecti}` : '', customer.value.urineLeakage ? `漏尿：${customer.value.urineLeakage}` : '', customer.value.pubicLumbago ? `腰痛/耻骨痛：${customer.value.pubicLumbago}` : '')],
  ['修复经历、产后检查、运动习惯', joinValues(customer.value.prevRepairExp, customer.value.postpartumCheck, customer.value.exerciseHabits)]
]);

const followupItems = computed<Array<[string, unknown]>>(() => [
  ['最近跟进', formatDate(customer.value.lastFollowupAt)],
  ['当前跟进内部备注', editField('internalNote')]
]);

const followupSummaryItems = computed<Array<[string, string]>>(() => [
  ['当前跟进内部备注', customer.value.internalNote || '-'],
  ['下次跟进', joinValues(formatDate(customer.value.nextFollowupAt), customer.value.nextFollowupDir)]
]);

const conversionItems = computed<Array<[string, unknown]>>(() => [
  ['首次追溯记录', editField('firstTrackingCapture')],
  ['第二次追溯记录', editField('secondTrackingCapture')],
  ['第三次追溯记录', editField('thirdTrackingCapture')]
]);

const appointmentItems = computed<Array<[string, unknown]>>(() => [
  ['预约状态', editField('appointmentStatus')],
  ['预约日期', editField('appointmentDate')],
  ['预约时间', editField('appointmentTime')],
  ['预约门店', editField('appointmentStore')],
  ['预约项目', editField('appointmentItem')],
  ['是否到店', editField('arrived')],
  ['到店来源行', customer.value.arrivalSourceRowId || '-'],
  ['最近跟进', formatDate(customer.value.lastFollowupAt)],
  ['同步时间', formatDate(customer.value.syncedAt)]
]);

const appointmentSummaryItems = computed<Array<[string, string]>>(() => [
  ['预约、核销、是否到店', joinValues(customer.value.appointmentStatus, customer.value.arrived)],
  ['预约时间与门店', joinValues(appointmentOverview.value, customer.value.appointmentStore)],
  ['预约项目', customer.value.appointmentItem || customer.value.intendedProject || '-'],
  ['到店来源行', customer.value.arrivalSourceRowId || '-'],
  ['同步时间', formatDate(customer.value.syncedAt)]
]);

const disposers: Array<() => void> = [];
let skipNextInput = false;

onMounted(() => {
  loadProfileById(props.customerId);
  if (props.readOnly) {
    return;
  }
  disposers.push(eventBus.on<CustomerSelectedPayload>('customer:selected', openSelectedCustomerProfile));
  disposers.push(eventBus.on<{ phone?: string; suggestions?: ProfileSuggestion[] }>('suggestion:show', appendProfileSuggestions));
  disposers.push(eventBus.on<StageSuggestPayload>('stage:suggest', appendStageSuggestion));
  disposers.push(eventBus.on<AbnormalAlertPayload>('abnormal:alert', handleProfileAbnormalAlert));
  disposers.push(eventBus.on<{ phone?: string; newStage?: string }>('stage:updated', handleStageUpdated));
  disposers.push(eventBus.on<{ phone?: string; customerId?: number | null }>('reply:send-confirmed', handleSendConfirmed));
  disposers.push(eventBus.on<CustomerTagsUpdatedPayload>('CUSTOMER_TAGS_UPDATED', handleCustomerTagsUpdated));
});

onBeforeUnmount(() => {
  if (props.readOnly) {
    return;
  }
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupCustomerProfileStore();
});

watch(() => props.customerId, (customerId) => {
  loadProfileById(customerId);
});

function loadProfileById(customerId?: number | null) {
  if (typeof customerId !== 'number' || customerId <= 0) {
    return;
  }
  void openProfile(customerId, 'PROFILE_CARD', '', { emitCustomerSelected: false, recoverPendingSave: false });
}

function onPaste(event: ClipboardEvent) {
  const text = event.clipboardData?.getData('text')?.trim();
  if (text) {
    skipNextInput = true;
    searchImmediately(text);
  }
}

function onInput() {
  if (skipNextInput) {
    skipNextInput = false;
    return;
  }
  scheduleSearch(state.keyword);
}

function refreshCurrent() {
  const phone = state.profile?.phoneFull || customer.value.phoneFull || customer.value.phone;
  const customerId = currentProfileCustomerId();
  if (customerId) {
    void openProfile(customerId, 'PROFILE_CARD');
  } else if (phone) {
    void openProfile(phone, 'PROFILE_CARD');
  }
}

function openCommunication(view: 'messages' | 'summaries'): void {
  const phone = state.profile?.phoneFull || customer.value.phoneFull || customer.value.phone;
  const customerId = currentProfileCustomerId();
  if (!phone && !customerId) {
    return;
  }
  eventBus.emit('communication:open', { view, phone: phone || undefined, customerId: customerId || undefined });
}

type CustomerSelectedPayload = {
  phone?: string;
  customerId?: number | null;
  sourceFrom?: SourceFrom;
  sessionId?: string;
};

function openSelectedCustomerProfile(payload: CustomerSelectedPayload) {
  const phone = payload.phone?.trim();
  const customerId = payload.customerId && payload.customerId > 0 ? payload.customerId : null;
  if (!phone && !customerId) {
    return;
  }
  if (!shouldOpenProfileFromSelectedEvent(payload.sourceFrom)) {
    return;
  }
  if (state.profileLoading && customerId && state.profile?.customer?.id === customerId) {
    return;
  }
  if (state.profileLoading && phone && (state.profile?.phoneFull || state.profile?.customer.phoneFull || state.profile?.customer.phone) === phone) {
    return;
  }
  void openProfile(customerId ?? phone ?? '', payload.sourceFrom ?? 'PROFILE_CARD', payload.sessionId ?? '');
}

function shouldOpenProfileFromSelectedEvent(sourceFrom?: SourceFrom): boolean {
  return !sourceFrom || ['DASHBOARD', 'FOLLOWUP_LIST', 'NEW_LEAD', 'CANDIDATE_LIST'].includes(sourceFrom);
}

function editField(key: keyof Customer): unknown {
  if (!state.editMode || props.readOnly) {
    return customer.value[key] ?? '-';
  }
  return h('input', {
    value: String(state.editFields[key as string] ?? ''),
    onInput: (event: Event) => {
      state.editFields[key as string] = (event.target as HTMLInputElement).value;
    }
  });
}

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购客资';
  if (value === 'XIAN_SUO') return '线索客资';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}

function maskPhone(phone: string): string {
  if (!phone) return '-';
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : `****${phone.slice(-4)}`;
}

function formatDate(value?: string | null): string {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function formatSearchDate(value?: string | null): string {
  const formatted = formatDate(value);
  return formatted === '-' ? formatted : formatted.slice(5);
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function joinValues(...values: Array<string | number | null | undefined>): string {
  const normalized = values
    .map((value) => String(value ?? '').trim())
    .filter(Boolean);
  return normalized.length ? normalized.join(' · ') : '-';
}

function weightText(label: string, value?: number | null): string {
  return value === null || value === undefined ? '' : `${label}体重：${value}kg`;
}

function toggleTagEdit(category: CustomerTagCategory): void {
  if (state.tagEditingCategoryId === category.id) {
    cancelTagEdit();
    return;
  }
  beginTagEdit(category.id);
}

function currentTagNames(categoryId: number): string {
  return (state.profile?.currentTags ?? [])
    .filter((tag) => tag.categoryId === categoryId)
    .map((tag) => tag.tagDisplayName || tag.tagValue)
    .join('、');
}

function isCategoryLocked(categoryId: number): boolean {
  return (state.profile?.tagLocks ?? []).some((lock) => lock.categoryId === categoryId && lock.locked);
}

function toggleCategoryLock(categoryId: number): void {
  const locked = isCategoryLocked(categoryId);
  void updateCustomerTagLock(
    categoryId,
    !locked,
    locked ? '员工解除分类锁定' : '员工锁定分类');
}

function draftSelection(category: CustomerTagCategory): number | number[] {
  const values = state.tagDrafts[category.id] ?? [];
  return category.selectionMode === 'SINGLE' ? values[0] ?? 0 : values;
}

function onTagSelectionChange(category: CustomerTagCategory, event: Event): void {
  const select = event.target as HTMLSelectElement;
  const values = Array.from(select.selectedOptions).map((option) => Number(option.value));
  state.tagDrafts[category.id] = category.selectionMode === 'SINGLE' ? values.slice(0, 1) : values;
}

function suggestionKey(suggestion: ProfileSuggestion): string {
  return `${suggestion.id ?? suggestion.suggestionId ?? suggestion.fieldName}-${String(suggestion.suggestedValue)}`;
}

const FieldGrid = defineComponent({
  props: {
    items: {
      type: Array as PropType<Array<[string, unknown]>>,
      required: true
    }
  },
  setup(props) {
    return () => h('div', { class: 'field-grid' }, props.items.map(([label, value]) =>
      h('div', { class: 'field-item' }, [
        h('span', { class: 'field-label' }, label),
        h('span', { class: 'field-value' }, [typeof value === 'object' && value !== null ? value as ReturnType<typeof h> : String(value ?? '-')])
      ])
    ));
  }
});

const ProfileFieldRows = defineComponent({
  props: {
    items: {
      type: Array as PropType<Array<[string, string]>>,
      required: true
    }
  },
  setup(props) {
    return () => h('div', { class: 'profile-field-rows' }, props.items.map(([label, value]) =>
      h('div', { class: 'profile-field-row' }, [
        h('span', { class: 'profile-field-row-label' }, label),
        h('span', { class: 'profile-field-row-value' }, value)
      ])
    ));
  }
});

</script>
