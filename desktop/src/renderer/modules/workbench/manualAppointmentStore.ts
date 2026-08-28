import { reactive } from 'vue';
import { getJson, postForm, postJson } from '../../shared/apiClient';
import { captureScreenshot, writeClipboardText } from '../../shared/desktopBridge';
import { resolveQuickSearchTemplate } from '../quick-search/templateVariables';
import { showWorkbenchSuccessToast } from './workbenchStore';

export type ManualAppointmentCandidate = { customerId: number; nickname?: string | null; phone?: string | null; intendedStore?: string | null };
export type ManualAppointmentField = { key: string; label: string; type: string; editable: boolean; options: string[] };
type ManualAppointmentForm = { customerId: number; customerVersion: number; nickname: string; phone: string; values: Record<string, string>; fields: ManualAppointmentField[] };
type ManualAppointmentSaveResult = {
  synced: boolean;
  taskId: number;
  syncError?: string | null;
  templateContent?: string | null;
  templateValues?: Record<string, string> | null;
};
type Report = { id: string; fileName: string };
type PendingReport = { id: string; file: File };

const emptyForm = (): ManualAppointmentForm => ({ customerId: 0, customerVersion: 0, nickname: '', phone: '', values: {}, fields: [] });

let matchRequestId = 0;
let matchAbortController: AbortController | null = null;

export const manualAppointmentState = reactive({
  visible: false,
  stage: 'matching' as 'matching' | 'select' | 'form',
  nickname: '',
  candidates: [] as ManualAppointmentCandidate[],
  form: emptyForm(),
  reportFiles: [] as PendingReport[],
  saving: false,
  error: '',
  notice: ''
});

export function openManualAppointment(): void {
  manualAppointmentState.visible = true;
  void matchCurrentChat();
}

export function closeManualAppointment(): void {
  matchRequestId += 1;
  matchAbortController?.abort();
  matchAbortController = null;
  manualAppointmentState.visible = false;
  manualAppointmentState.error = '';
  manualAppointmentState.notice = '';
  manualAppointmentState.candidates = [];
  manualAppointmentState.form = emptyForm();
  manualAppointmentState.reportFiles = [];
}

export function addManualAppointmentReports(files: FileList | File[]): void {
  for (const file of Array.from(files)) {
    if (!['image/jpeg', 'image/png', 'image/webp', 'application/pdf'].includes(file.type)) {
      manualAppointmentState.error = `“${file.name}”不是支持的客户报告文件`;
      continue;
    }
    const id = typeof crypto?.randomUUID === 'function' ? crypto.randomUUID() : `${Date.now()}-${file.name}`;
    manualAppointmentState.reportFiles.push({ id, file });
  }
}

export function removeManualAppointmentReport(id: string): void {
  manualAppointmentState.reportFiles = manualAppointmentState.reportFiles.filter((item) => item.id !== id);
}

export async function matchCurrentChat(): Promise<void> {
  const requestId = ++matchRequestId;
  matchAbortController?.abort();
  const abortController = new AbortController();
  matchAbortController = abortController;
  manualAppointmentState.stage = 'matching';
  manualAppointmentState.error = '';
  manualAppointmentState.notice = '';
  manualAppointmentState.candidates = [];
  manualAppointmentState.nickname = '';
  try {
    const capture = await captureScreenshot();
    if (requestId !== matchRequestId || abortController.signal.aborted) return;
    if (!capture.success || !capture.imageBase64) throw new Error('无法截取当前微信聊天窗口');
    const response = await postJson<{ nickname?: string; candidates?: ManualAppointmentCandidate[] }>(
      '/api/v1/arrival-handover/current-customer',
      { imageBase64: capture.imageBase64 },
      undefined,
      abortController.signal
    );
    if (requestId !== matchRequestId || abortController.signal.aborted) return;
    if (!response.success || !response.data) throw new Error(response.message || '当前微信客户匹配失败');
    manualAppointmentState.nickname = response.data.nickname || '';
    manualAppointmentState.candidates = response.data.candidates || [];
    if (manualAppointmentState.candidates.length === 1) {
      await chooseManualAppointmentCustomer(manualAppointmentState.candidates[0].customerId);
      return;
    }
    manualAppointmentState.stage = 'select';
  } catch (error) {
    if (requestId !== matchRequestId || abortController.signal.aborted) return;
    manualAppointmentState.error = error instanceof Error ? error.message : '当前微信客户匹配失败';
    manualAppointmentState.stage = 'select';
  } finally {
    if (requestId === matchRequestId) matchAbortController = null;
  }
}

export async function chooseManualAppointmentCustomer(customerId: number): Promise<void> {
  manualAppointmentState.error = '';
  manualAppointmentState.notice = '';
  manualAppointmentState.stage = 'matching';
  try {
    const response = await getJson<ManualAppointmentForm>(`/api/v1/arrival-handover/customers/${customerId}/appointment`);
    if (!response.success || !response.data) throw new Error(response.message || '客户资料加载失败');
    manualAppointmentState.form = { ...response.data, values: { ...response.data.values }, fields: [...response.data.fields] };
    manualAppointmentState.stage = 'form';
  } catch (error) {
    manualAppointmentState.error = error instanceof Error ? error.message : '客户资料加载失败';
    manualAppointmentState.stage = 'select';
  }
}

export async function submitManualAppointment(): Promise<void> {
  manualAppointmentState.saving = true;
  manualAppointmentState.error = '';
  manualAppointmentState.notice = '';
  try {
    const form = manualAppointmentState.form;
    let response = await saveManualAppointment(form);
    if (!response.success && response.errorCode === '50-10002') {
      const latest = await getJson<ManualAppointmentForm>(`/api/v1/arrival-handover/customers/${form.customerId}/appointment`);
      if (!latest.success || !latest.data) {
        throw new Error('客户资料刚有更新，无法读取最新版本，请直接再次提交');
      }
      form.customerVersion = latest.data.customerVersion;
      response = await saveManualAppointment(form);
    }
    if (!response.success || !response.data) throw new Error(response.message || '预约保存失败');
    let result = response.data;
    if (manualAppointmentState.reportFiles.length) {
      const reports: Report[] = [];
      for (const pending of manualAppointmentState.reportFiles) {
        const data = new FormData();
        data.append('file', pending.file);
        const uploaded = await postForm<Report>(`/api/v1/arrival-handover/tasks/${result.taskId}/reports`, data);
        if (!uploaded.success || !uploaded.data) throw new Error(`“${pending.file.name}”上传失败，预约记录已保存`);
        reports.push(uploaded.data);
      }
      const committed = await postJson<{ synced: boolean; syncError?: string | null }>(`/api/v1/arrival-handover/tasks/${result.taskId}/reports/commit`, { reports });
      if (!committed.success || !committed.data) throw new Error(committed.message || '客户报告已上传，但保存关联失败');
      result = { ...result, synced: committed.data.synced, syncError: committed.data.syncError };
    }
    if (result.templateContent) {
      const templateValues = { ...form.values, ...(result.templateValues ?? {}), nickname: form.nickname, phone: form.phone };
      const text = removeUnresolvedTemplateLines(resolveQuickSearchTemplate(result.templateContent, templateValues, form.phone));
      const copied = await writeClipboardText(text);
      if (!copied.success) throw new Error('预约已保存，但预约成功话术复制失败');
    }
    if (!result.synced) {
      manualAppointmentState.error = `预约已保存，到店表同步失败${result.syncError ? `：${result.syncError}` : ''}，系统会自动重试。`;
    } else {
      const successMessage = result.templateContent
        ? '保存成功，已复制预约话术'
        : '保存成功，尚未配置预约成功话术';
      closeManualAppointment();
      showWorkbenchSuccessToast(successMessage);
    }
  } catch (error) {
    manualAppointmentState.error = error instanceof Error ? error.message : '预约保存失败';
  } finally {
    manualAppointmentState.saving = false;
  }
}

function saveManualAppointment(form: ManualAppointmentForm) {
  return postJson<ManualAppointmentSaveResult>(
    `/api/v1/arrival-handover/customers/${form.customerId}/appointment`,
    { customerVersion: form.customerVersion, values: form.values }
  );
}

function removeUnresolvedTemplateLines(text: string): string {
  return text.split(/\r?\n/).filter((line) => !/\{\{[^{}]+\}\}|\{[^{}]+\}/.test(line)).join('\n');
}
