<template>
  <div v-if="open" class="reply-task-drawer-backdrop" @click.self="emit('close')">
    <aside class="reply-task-drawer" aria-label="回复任务列表">
      <header>
        <div>
          <h2>回复任务</h2>
          <p>最近任务</p>
        </div>
        <div class="reply-task-drawer-actions">
          <button
            v-if="tasks.length"
            class="reply-task-drawer-archive"
            type="button"
            data-testid="archive-reply-tasks"
            @click="emit('archive')"
          >
            清空队列
          </button>
          <button class="reply-task-drawer-close" type="button" aria-label="关闭回复任务列表" title="关闭回复任务列表" @click="emit('close')">
            <span aria-hidden="true">×</span>
          </button>
        </div>
      </header>
      <div v-if="visibleTasks.length" class="reply-task-drawer-list">
        <article
          v-for="task in visibleTasks"
          :key="task.sessionId"
          class="reply-task-drawer-row"
          :class="{ active: task.sessionId === activeSessionId }"
        >
          <button
            class="reply-task-drawer-select"
            type="button"
            :data-testid="`reply-task-drawer-row-${task.sessionId}`"
            @click="emit('select', task.sessionId)"
          >
            <span class="reply-task-drawer-copy">
              <strong>{{ nicknameFor(task) }}</strong>
              <small v-if="task.archived">已暂存</small>
            </span>
            <span class="reply-task-drawer-status">{{ statusLabel(task.status) }}</span>
          </button>
          <button
            v-if="canCancel(task)"
            class="reply-task-drawer-cancel"
            type="button"
            :aria-label="`取消${nicknameFor(task)}的识图任务`"
            title="取消识图任务"
            :data-testid="`cancel-reply-task-${task.jobId}`"
            @click="emit('cancel', task.jobId, task.sessionId)"
          >
            <span aria-hidden="true">×</span>
          </button>
        </article>
      </div>
      <p v-else class="reply-task-drawer-empty">暂无回复任务</p>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type TaskItem = {
  sessionId: string;
  nickname?: string;
  status: string;
  updatedAt: number;
  jobId?: string;
  archived?: boolean;
};

const props = defineProps<{
  open: boolean;
  tasks: TaskItem[];
  activeSessionId: string;
}>();

const emit = defineEmits<{
  close: [];
  select: [sessionId: string];
  archive: [];
  cancel: [jobId: string, sessionId: string];
}>();

const visibleTasks = computed(() => props.tasks.slice(0, 30));

function nicknameFor(task: TaskItem): string {
  return task.nickname?.trim() || '未识别客户';
}

function statusLabel(status: string): string {
  if (status === 'QUEUED') return '排队中';
  if (status === 'RECOGNIZING' || status === 'LOADING') return '识别中';
  if (status === 'WAITING_CUSTOMER' || status === 'MULTIPLE') return '待选择';
  if (status === 'READY' || status === 'FALLBACK') return '可复制';
  if (status === 'COPIED') return '已复制';
  if (status === 'CANCELLED') return '已取消';
  if (status === 'EXPIRED') return '已过期';
  return '失败';
}

function canCancel(task: TaskItem): task is TaskItem & { jobId: string } {
  return Boolean(task.jobId) && (task.status === 'QUEUED' || task.status === 'RECOGNIZING');
}
</script>

<style scoped>
.reply-task-drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 55;
  display: flex;
  justify-content: flex-start;
  background: rgb(15 23 42 / 28%);
}

.reply-task-drawer {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: min(360px, 88vw);
  height: 100%;
  border-right: 1px solid var(--border-color, #d6dee8);
  background: var(--surface-color, #ffffff);
  box-shadow: 12px 0 32px rgb(15 23 42 / 16%);
}

.reply-task-drawer header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px;
  border-bottom: 1px solid var(--border-color, #d6dee8);
}

.reply-task-drawer-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.reply-task-drawer-archive {
  min-height: 30px;
  border: 1px solid var(--border-color, #d6dee8);
  background: transparent;
  color: var(--text-primary, #1e293b);
  cursor: pointer;
  font-size: 12px;
}

.reply-task-drawer h2,
.reply-task-drawer p {
  margin: 0;
}

.reply-task-drawer h2 {
  font-size: 18px;
}

.reply-task-drawer p {
  margin-top: 4px;
  color: var(--text-muted, #64748b);
  font-size: 13px;
}

.reply-task-drawer-close {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid var(--border-color, #d6dee8);
  background: transparent;
  cursor: pointer;
}

.reply-task-drawer-list {
  display: grid;
  align-content: start;
  gap: 4px;
  overflow-y: auto;
  padding: 10px;
}

.reply-task-drawer-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: stretch;
  gap: 4px;
}

.reply-task-drawer-select {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  min-height: 48px;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-primary, #1e293b);
  cursor: pointer;
  text-align: left;
}

.reply-task-drawer-select:hover,
.reply-task-drawer-select:focus-visible,
.reply-task-drawer-row.active .reply-task-drawer-select {
  border-color: var(--accent-color, #2563eb);
  background: var(--accent-soft, #eff6ff);
}

.reply-task-drawer-cancel {
  width: 30px;
  border: 1px solid var(--border-color, #d6dee8);
  background: transparent;
  color: var(--text-muted, #64748b);
  cursor: pointer;
}

.reply-task-drawer-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.reply-task-drawer-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.reply-task-drawer-copy small,
.reply-task-drawer-status {
  color: var(--text-muted, #64748b);
  font-size: 12px;
}

.reply-task-drawer-status {
  white-space: nowrap;
}

.reply-task-drawer-empty {
  padding: 18px;
}
</style>
