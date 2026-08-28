<template>
  <section v-if="state.visible" class="manual-appointment-overlay" aria-label="手动预约">
    <div class="manual-appointment-backdrop"></div>
    <section class="manual-appointment-dialog" role="dialog" aria-modal="true" aria-label="预约">
      <header class="manual-appointment-head">
        <div><h2>预约</h2></div>
        <button class="secondary small icon-button" type="button" aria-label="关闭" title="关闭" @click="closeManualAppointment">×</button>
      </header>

      <div v-if="state.stage === 'matching'" class="manual-appointment-loading">正在识别当前微信客户并匹配档案...</div>
      <section v-else-if="state.stage === 'select'" class="manual-appointment-select">
        <p v-if="state.nickname">已识别当前微信客户：{{ state.nickname }}</p>
        <p v-else>没有识别到当前微信客户。</p>
        <p v-if="state.error" class="arrival-form-error" role="alert">{{ state.error }}</p>
        <p v-if="!state.candidates.length" class="arrival-form-error">请切换到客户聊天窗口后重试，或先使用工作台的客户档案查找。</p>
        <div v-else class="manual-appointment-candidates">
          <button v-for="candidate in state.candidates" :key="candidate.customerId" class="secondary" type="button" @click="void chooseManualAppointmentCustomer(candidate.customerId)">
            <strong>{{ candidate.nickname || `客户 ${candidate.phone?.slice(-4)}` }}</strong><span>{{ candidate.intendedStore || '未登记意向门店' }}</span>
          </button>
        </div>
        <footer><button class="secondary" type="button" @click="void matchCurrentChat()">重新识别</button></footer>
      </section>

      <form v-else-if="state.stage === 'form'" class="manual-appointment-form" @submit.prevent="void submitManualAppointment()">
        <div class="manual-appointment-customer"><strong>{{ customerLabel }}</strong><span>{{ state.form.phone || '-' }}</span></div>
        <div class="manual-appointment-grid">
          <template v-for="field in editableFields" :key="field.key">
            <label :class="{ 'manual-appointment-wide': isLongText(field) }">
              <span>{{ field.label }}</span>
              <input v-if="field.key === 'assignedKeeper'" v-model="state.form.values[field.key]" type="text" readonly />
              <div v-else-if="field.key === 'appointmentTime'" class="manual-appointment-time-control">
                <select :value="appointmentPeriod" aria-label="到店时间上下午" @change="updateAppointmentTimePart('period', ($event.target as HTMLSelectElement).value)">
                  <option value="">请选择</option><option value="AM">上午</option><option value="PM">下午</option>
                </select>
                <input :value="appointmentHour" aria-label="到店时间小时" type="number" min="1" max="12" inputmode="numeric" placeholder="时" @input="updateAppointmentTimePart('hour', ($event.target as HTMLInputElement).value)" />
                <span class="manual-appointment-time-separator">:</span>
                <input :value="appointmentMinute" aria-label="到店时间分钟" type="number" min="0" max="59" inputmode="numeric" placeholder="分" @input="updateAppointmentTimePart('minute', ($event.target as HTMLInputElement).value)" />
              </div>
              <template v-else-if="isSuggestedInputField(field)">
                <div class="manual-appointment-combobox" :class="{ 'is-open': isSuggestedFieldOpen(field) }" @focusout="closeSuggestedFieldLater(field.key)">
                  <input v-model="state.form.values[field.key]" type="text" role="combobox" aria-autocomplete="list" :aria-expanded="isSuggestedFieldOpen(field)" :aria-controls="suggestionListId(field)" :placeholder="suggestedInputPlaceholder(field)" :readonly="!field.editable" @focus="openSuggestedField(field)" @keydown.esc="closeSuggestedField(field.key)" @keydown.down.prevent="openSuggestedField(field)" />
                  <button class="manual-appointment-combobox-toggle" type="button" :aria-label="`${field.label}候选项`" :aria-expanded="isSuggestedFieldOpen(field)" :aria-controls="suggestionListId(field)" :disabled="!field.editable || !field.options.length" @mousedown.prevent @click="toggleSuggestedField(field)" />
                  <ul v-if="isSuggestedFieldOpen(field)" :id="suggestionListId(field)" class="manual-appointment-combobox-options" role="listbox" :aria-label="`${field.label}候选项`">
                    <li v-for="option in matchingOptions(field)" :key="option" role="option" :aria-selected="state.form.values[field.key] === option"><button type="button" @mousedown.prevent @click="selectSuggestedOption(field, option)">{{ option }}</button></li>
                    <li v-if="!matchingOptions(field).length" class="manual-appointment-combobox-empty">没有匹配项</li>
                  </ul>
                </div>
              </template>
              <select v-else-if="field.options.length" v-model="state.form.values[field.key]" :disabled="!field.editable">
                <option value="">未填写</option><option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
              </select>
              <textarea v-else-if="isLongText(field)" v-model="state.form.values[field.key]" :readonly="!field.editable" rows="3" />
              <input v-else v-model="state.form.values[field.key]" :type="inputType(field)" :readonly="!field.editable" />
            </label>
          </template>
        </div>
        <label v-if="reportField" class="manual-appointment-report"><span>{{ reportField.label }}</span>
          <div class="arrival-dropzone" :class="{ dragging }" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="onDrop">
            <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp,application/pdf" multiple @change="onFiles">
            <button class="secondary small" type="button" @click="fileInput?.click()">选择文件</button><p>拖拽图片或 PDF 到此处</p>
          </div>
        </label>
        <ul v-if="state.reportFiles.length" class="arrival-report-list"><li v-for="report in state.reportFiles" :key="report.id"><span>{{ report.file.name }}</span><button class="link-button" type="button" @click="removeManualAppointmentReport(report.id)">移除</button></li></ul>
        <p v-if="state.error" class="arrival-form-error manual-appointment-submit-status" role="alert">{{ state.error }}</p>
        <p v-else-if="state.notice" class="manual-appointment-notice manual-appointment-submit-status" role="status">{{ state.notice }}</p>
        <footer class="arrival-dialog-actions"><p v-if="state.saving" class="manual-appointment-saving" role="status">正在保存预约并同步到店表...</p><button class="secondary" type="button" :disabled="state.saving" @click="closeManualAppointment">取消</button><button class="primary" type="submit" :disabled="state.saving">{{ state.saving ? '保存中...' : '提交预约并复制话术' }}</button></footer>
      </form>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  addManualAppointmentReports,
  chooseManualAppointmentCustomer,
  closeManualAppointment,
  type ManualAppointmentField,
  manualAppointmentState as state,
  matchCurrentChat,
  removeManualAppointmentReport,
  submitManualAppointment
} from './manualAppointmentStore';

const fileInput = ref<HTMLInputElement>();
const dragging = ref(false);
const openedSuggestedField = ref<string | null>(null);
const customerLabel = computed(() => state.form.nickname || state.nickname || '当前客户');
const reportField = computed(() => state.form.fields.find((field) => field.type === 'FIELD_TYPE_IMAGE'));
const appointmentTimeParts = computed(() => splitAppointmentTime(state.form.values.appointmentTime));
const appointmentPeriod = computed(() => appointmentTimeParts.value.period);
const appointmentHour = computed(() => appointmentTimeParts.value.hour);
const appointmentMinute = computed(() => appointmentTimeParts.value.minute);
const hiddenFieldKeys = new Set([
  'arrived',
  'transactionAmount',
  'transactionAt',
  'transactionPrimaryReason',
  'appointmentDateTime',
  'appointmentItem'
]);
const editableFields = computed(() => state.form.fields.filter((field) => field.type !== 'FIELD_TYPE_IMAGE' && !hiddenFieldKeys.has(field.key)));
function inputType(field: ManualAppointmentField): string {
  if (field.type === 'FIELD_TYPE_DATE_TIME') return field.key === 'appointmentDate' ? 'date' : 'datetime-local';
  if (field.type === 'FIELD_TYPE_NUMBER') return 'number';
  if (field.type === 'FIELD_TYPE_PHONE_NUMBER') return 'tel';
  return 'text';
}
function splitAppointmentTime(value: string | undefined): { period: string; hour: string; minute: string } {
  const match = String(value ?? '').trim().match(/^(\d{1,2}):(\d{2})/);
  if (!match) return { period: '', hour: '', minute: '' };
  const hours = Number(match[1]);
  if (!Number.isFinite(hours) || hours < 0 || hours > 23) return { period: '', hour: '', minute: '' };
  return { period: hours >= 12 ? 'PM' : 'AM', hour: String(hours % 12 || 12), minute: match[2] };
}
function updateAppointmentTimePart(part: 'period' | 'hour' | 'minute', rawValue: string): void {
  const current = appointmentTimeParts.value;
  const period = part === 'period' ? rawValue : current.period;
  const hour = part === 'hour' ? rawValue.replace(/\D/g, '').slice(0, 2) : current.hour;
  const minute = part === 'minute' ? rawValue.replace(/\D/g, '').slice(0, 2) : current.minute;
  if (!period || !hour || !minute) { state.form.values.appointmentTime = ''; return; }
  const hourNumber = Number(hour);
  const minuteNumber = Number(minute);
  if (hourNumber < 1 || hourNumber > 12 || minuteNumber < 0 || minuteNumber > 59) return;
  const hours24 = period === 'PM' ? (hourNumber % 12) + 12 : hourNumber % 12;
  state.form.values.appointmentTime = `${String(hours24).padStart(2, '0')}:${String(minuteNumber).padStart(2, '0')}`;
}
function isLongText(field: ManualAppointmentField): boolean { return ['客户主诉', '私域客情', '成交主因'].includes(field.label); }
function isSuggestedInputField(field: ManualAppointmentField): boolean { return ['receptionTeacher', 'receptionConsultant', 'sourceChannel', 'arrivalExperienceProject'].includes(field.key); }
function suggestedInputPlaceholder(field: ManualAppointmentField): string {
  if (field.options.length) return `输入${field.label}或从建议中选择`;
  return `输入${field.label}`;
}
function suggestionListId(field: ManualAppointmentField): string { return `manual-appointment-options-${field.key}`; }
function isSuggestedFieldOpen(field: ManualAppointmentField): boolean { return openedSuggestedField.value === field.key && field.options.length > 0; }
function openSuggestedField(field: ManualAppointmentField): void { if (field.editable && field.options.length) openedSuggestedField.value = field.key; }
function toggleSuggestedField(field: ManualAppointmentField): void { openedSuggestedField.value = isSuggestedFieldOpen(field) ? null : field.key; }
function closeSuggestedField(fieldKey: string): void { if (openedSuggestedField.value === fieldKey) openedSuggestedField.value = null; }
function closeSuggestedFieldLater(fieldKey: string): void { window.setTimeout(() => closeSuggestedField(fieldKey), 120); }
function matchingOptions(field: ManualAppointmentField): string[] {
  const query = String(state.form.values[field.key] ?? '').trim().toLocaleLowerCase();
  return field.options.filter((option) => !query || option.toLocaleLowerCase().includes(query)).slice(0, 80);
}
function selectSuggestedOption(field: ManualAppointmentField, option: string): void { state.form.values[field.key] = option; closeSuggestedField(field.key); }
function onFiles(event: Event): void { const input = event.target as HTMLInputElement; if (input.files) addManualAppointmentReports(input.files); input.value = ''; }
function onDrop(event: DragEvent): void { dragging.value = false; if (event.dataTransfer?.files) addManualAppointmentReports(event.dataTransfer.files); }
</script>
