<template>
  <p v-if="state.notice" class="arrival-handover-notice" role="status">{{ state.notice }}</p>
  <section v-if="state.loaded && state.tasks.length" class="arrival-handover-panel" aria-label="待补充到店资料">
    <header class="section-inline-head">
      <div><h3>待补充到店资料</h3><small>{{ ownTasks.length }} 项由你填写</small></div>
      <button class="link-button" type="button" @click="void loadTasks(true)">刷新</button>
    </header>
    <div v-for="group in groups" :key="group.keeper" class="arrival-task-group">
      <p class="arrival-task-group-name">{{ group.own ? '我的任务' : group.keeper }}</p>
      <article v-for="task in group.tasks" :key="task.id" class="arrival-task-row">
        <button class="arrival-task-summary" type="button" @click="openTask(task)">
          <strong>{{ task.nickname || `客户 ${task.phone.slice(-4)}` }}</strong>
          <span>{{ task.appointmentDate }}{{ task.appointmentTime ? ` ${task.appointmentTime}` : '' }} · {{ task.appointmentStore }}{{ task.appointmentItem ? ` · ${task.appointmentItem}` : '' }}</span>
        </button>
        <button v-if="task.canComplete" class="primary small" type="button" @click="openTask(task)">继续填写</button>
        <button v-else class="secondary small" type="button" @click="void remind(task)">提醒填写</button>
      </article>
    </div>
  </section>

  <dialog ref="dialog" class="arrival-dialog" @close="closeDialog">
    <form v-if="selected" class="arrival-form" @submit.prevent="complete">
      <header class="arrival-dialog-head"><div><h2>补充到店资料</h2><p>{{ selected.nickname || maskPhone(selected.phone) }} · {{ selected.appointmentDate }} {{ selected.appointmentTime }} · {{ selected.appointmentStore }}</p></div><button class="secondary small icon-button" type="button" aria-label="关闭" title="关闭" @click="dialog?.close()">×</button></header>
      <div v-if="!selected.canComplete" class="arrival-readonly"><span>该事项由 {{ selected.assignedKeeper || '负责人' }} 填写</span><button class="secondary small" type="button" @click="void remind(selected)">提醒填写</button></div>
      <template v-else>
        <div class="arrival-field-grid">
          <label v-for="field in selectFields" :key="field.key"><span>{{ field.label }}</span><select v-model="form[field.key]" required><option value="" disabled>请选择</option><option v-for="option in options[field.label] || []" :key="option" :value="option">{{ option }}</option></select></label>
        </div>
        <label class="arrival-report-field"><span>客户报告 <small>可选</small></span><div class="arrival-dropzone" :class="{ dragging: dragging }" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="onDrop"><input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp,application/pdf" multiple @change="onFiles"><button class="secondary small" type="button" @click="fileInput?.click()">选择文件</button><p>拖拽图片或 PDF 到此处</p></div></label>
        <ul v-if="reportFiles.length" class="arrival-report-list"><li v-for="file in reportFiles" :key="file.id"><span>{{ file.fileName }}</span><button class="link-button" type="button" @click="removeReport(file.id)">移除</button></li></ul>
        <p v-if="state.error" class="arrival-form-error">{{ state.error }}</p>
        <footer class="arrival-dialog-actions"><button class="secondary" type="button" :disabled="state.saving" @click="dialog?.close()">取消</button><button class="primary" type="submit" :disabled="state.saving || optionsLoading">{{ state.saving ? '保存中...' : '完成并同步到店表' }}</button></footer>
      </template>
    </form>
  </dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { getJson, postForm, postJson } from '../../shared/apiClient';
import { notifyReplyTask, onReplyTaskOpen } from '../../shared/desktopBridge';

type Task = { id: number; phone: string; nickname?: string | null; assignedKeeper?: string | null; appointmentDate: string; appointmentTime?: string | null; appointmentStore: string; appointmentItem?: string | null; canComplete: boolean; canRemind: boolean; remindedAt?: string | null };
type Report = { id: string; fileName: string };
type FormKey = 'visitType' | 'voucherRedeemed' | 'experienceProject' | 'projectType' | 'historicalExperienceCount';
type CompletionResult = { databaseSaved: boolean; synced: boolean; wecomRowId?: string | null; syncError?: string | null };
const state = reactive({ loaded: false, saving: false, error: '', notice: '', tasks: [] as Task[] });
const dialog = ref<HTMLDialogElement>(); const fileInput = ref<HTMLInputElement>(); const selected = ref<Task>(); const options = ref<Record<string, string[]>>({}); const optionsLoading = ref(false); const dragging = ref(false); const reportFiles = ref<Report[]>([]); const notified = new Set<number>(); let timer: number | undefined; let disposeOpen: () => void = () => undefined;
const form = reactive<Record<FormKey, string>>({ visitType: '', voucherRedeemed: '', experienceProject: '', projectType: '', historicalExperienceCount: '' });
let noticeTimer: number | undefined;
const selectFields: Array<{ key: FormKey; label: string }> = [{ key: 'visitType', label: '类型' }, { key: 'voucherRedeemed', label: '是否核券' }, { key: 'experienceProject', label: '体验项目' }, { key: 'projectType', label: '项目类型' }, { key: 'historicalExperienceCount', label: '历史体验次数' }];
const ownTasks = computed(() => state.tasks.filter((task) => task.canComplete));
const groups = computed(() => { const map = new Map<string, { keeper: string; own: boolean; tasks: Task[] }>(); [...state.tasks].sort((a,b) => Number(b.canComplete) - Number(a.canComplete) || (a.assignedKeeper || '').localeCompare(b.assignedKeeper || '')).forEach((task) => { const own = task.canComplete; const keeper = own ? '我的任务' : task.assignedKeeper || '未分配负责人'; const group = map.get(keeper) ?? { keeper, own, tasks: [] }; group.tasks.push(task); map.set(keeper, group); }); return [...map.values()]; });
onMounted(() => { void loadTasks(); timer = window.setInterval(() => void loadTasks(), 30_000); disposeOpen = onReplyTaskOpen(({ taskId }) => { if (!taskId.startsWith('arrival-')) return; const task = state.tasks.find((item) => item.id === Number(taskId.slice(8))); if (task) openTask(task); }); });
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); if (noticeTimer) window.clearTimeout(noticeTimer); disposeOpen(); });
async function loadTasks(manual = false): Promise<void> { try { const response = await getJson<Task[]>('/api/v1/arrival-handover/tasks'); if (!response.success || !response.data) throw new Error(response.message || '加载失败'); const previous = new Set(state.tasks.map((task) => task.id)); state.tasks = response.data; state.loaded = true; for (const task of state.tasks.filter((item) => item.canComplete && !previous.has(item.id))) { void notify(task); if (!selected.value && !manual) openTask(task); } } catch { if (manual) state.error = '任务加载失败，请检查网络后重试'; } }
async function openTask(task: Task): Promise<void> { selected.value = task; state.error = ''; reportFiles.value = []; Object.keys(form).forEach((key) => { form[key as FormKey] = ''; }); if (!dialog.value?.open) dialog.value?.showModal(); if (Object.keys(options.value).length) return; optionsLoading.value = true; try { const response = await getJson<Record<string, string[]>>('/api/v1/arrival-handover/options'); if (!response.success || !response.data) throw new Error(); options.value = response.data; } catch { state.error = '到店表选项读取失败，请稍后重试'; } finally { optionsLoading.value = false; } }
function closeDialog(): void { selected.value = undefined; state.error = ''; dragging.value = false; }
async function onFiles(event: Event): Promise<void> { const input = event.target as HTMLInputElement; await uploadFiles(input.files); input.value = ''; }
async function onDrop(event: DragEvent): Promise<void> { dragging.value = false; await uploadFiles(event.dataTransfer?.files); }
async function uploadFiles(files: FileList | null | undefined): Promise<void> { if (!selected.value || !files?.length) return; for (const file of Array.from(files)) { const data = new FormData(); data.append('file', file); try { const response = await postForm<Report>(`/api/v1/arrival-handover/tasks/${selected.value.id}/reports`, data); if (!response.success || !response.data) throw new Error(); reportFiles.value.push(response.data); } catch { state.error = `“${file.name}”上传失败`; return; } } }
function removeReport(id: string): void { reportFiles.value = reportFiles.value.filter((file) => file.id !== id); }
async function complete(): Promise<void> { if (!selected.value) return; state.saving = true; state.error = ''; try { const response = await postJson<CompletionResult>(`/api/v1/arrival-handover/tasks/${selected.value.id}/complete`, { ...form, reports: reportFiles.value }); if (!response.success) throw new Error(response.message || '保存失败'); if (response.data && !response.data.synced) { state.error = `资料已保存，但到店表同步失败${response.data.syncError ? `：${response.data.syncError}` : ''}。系统会自动重试。`; return; } const id = selected.value.id; state.tasks = state.tasks.filter((task) => task.id !== id); dialog.value?.close(); showNotice('到店资料已保存，并已同步到店表'); } catch (error) { state.error = error instanceof Error ? error.message : '保存失败，请重试'; } finally { state.saving = false; } }
async function remind(task: Task): Promise<void> { try { const response = await postJson<void>(`/api/v1/arrival-handover/tasks/${task.id}/remind`, {}); if (!response.success) throw new Error(); task.remindedAt = new Date().toISOString(); state.error = ''; } catch { state.error = '提醒发送失败，请稍后重试'; } }
async function notify(task: Task): Promise<void> { if (notified.has(task.id)) return; notified.add(task.id); await notifyReplyTask({ taskId: `arrival-${task.id}`, title: '请补充到店资料', body: `${task.nickname || '客户'}已确认预约，请填写到店资料。` }); }
function maskPhone(phone: string): string { return phone.length > 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone; }
function showNotice(message: string): void { state.notice = message; if (noticeTimer) window.clearTimeout(noticeTimer); noticeTimer = window.setTimeout(() => { state.notice = ''; noticeTimer = undefined; }, 8000); }
</script>
