<template>
  <section class="reply-task-sidebar" aria-label="回复任务">
    <div class="reply-task-sidebar-head">
      <span>回复任务</span>
      <button
        class="reply-task-sidebar-more"
        type="button"
        aria-label="查看全部回复任务"
        title="查看全部回复任务"
        data-testid="open-reply-task-drawer"
        @click="emit('openAll')"
      >
        <span aria-hidden="true">≡</span>
      </button>
    </div>
    <div v-if="visibleTasks.length" class="reply-task-sidebar-list">
      <button
        v-for="task in visibleTasks"
        :key="task.sessionId"
        class="reply-task-sidebar-row"
        :class="{ active: task.sessionId === activeSessionId }"
        type="button"
        :data-testid="`reply-task-row-${task.sessionId}`"
        @click="emit('select', task.sessionId)"
      >
        <strong>{{ nicknameFor(task) }}</strong>
        <span>{{ statusLabel(task.status) }}</span>
      </button>
    </div>
    <p v-else class="reply-task-sidebar-empty">暂无任务</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type TaskItem = {
  sessionId: string;
  nickname?: string;
  status: string;
};

const props = defineProps<{
  tasks: TaskItem[];
  activeSessionId: string;
}>();

const emit = defineEmits<{
  select: [sessionId: string];
  openAll: [];
}>();

const visibleTasks = computed(() => props.tasks.slice(0, 5));

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
</script>

<style scoped>
.reply-task-sidebar {
  display: grid;
  gap: 6px;
  min-height: 0;
}

.reply-task-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted, #64748b);
  font-size: 12px;
}

.reply-task-sidebar-more {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 1px solid var(--border-color, #d6dee8);
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.reply-task-sidebar-more:hover,
.reply-task-sidebar-more:focus-visible {
  border-color: var(--accent-color, #2563eb);
  color: var(--accent-color, #2563eb);
}

.reply-task-sidebar-list {
  display: grid;
  gap: 4px;
}

.reply-task-sidebar-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 28px;
  gap: 6px;
  padding: 4px 6px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-primary, #1e293b);
  cursor: pointer;
  text-align: left;
}

.reply-task-sidebar-row strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 600;
}

.reply-task-sidebar-row span {
  color: var(--text-muted, #64748b);
  font-size: 11px;
  white-space: nowrap;
}

.reply-task-sidebar-row:hover,
.reply-task-sidebar-row:focus-visible,
.reply-task-sidebar-row.active {
  border-color: var(--accent-color, #2563eb);
  background: var(--accent-soft, #eff6ff);
}

.reply-task-sidebar-empty {
  margin: 0;
  color: var(--text-muted, #64748b);
  font-size: 12px;
}
</style>
