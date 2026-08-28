<template>
  <div class="ops-drawer-backdrop ops-modal-backdrop" @click.self="close">
    <section class="ops-drawer ops-modal-form assignment-table-manager-modal" role="dialog" aria-modal="true" aria-labelledby="assignment-table-manager-title">
      <header>
        <div>
          <h2 id="assignment-table-manager-title">表格管理</h2>
          <p>创建并切换月度分配表，旧表会保留，方便回看当月客资。</p>
        </div>
        <button class="icon-close-button" type="button" aria-label="关闭表格管理" title="关闭" :disabled="creating" @click="close">
          <span aria-hidden="true">×</span>
        </button>
      </header>

      <p v-if="loadError" class="admin-message error">{{ loadError }}</p>
      <p v-else-if="loading" class="ops-empty">正在读取分配表...</p>

      <section v-else class="assignment-table-manager-content">
        <div v-if="activeTable" class="assignment-table-manager-current">
          <span>当前使用</span>
          <strong>{{ activeTable.tableName }}</strong>
          <small>{{ activeTable.monthKey }} · 已完成绑定</small>
          <button class="secondary small" type="button" :disabled="opening" @click="openTable(activeTable)">打开表格</button>
        </div>
        <p v-else class="ops-empty">还没有可用的分配表，请先创建一张。</p>

        <form class="assignment-table-manager-create" @submit.prevent="submitCreate">
          <label>
            新表名称
            <input v-model.trim="draftName" type="text" maxlength="200" placeholder="例如：9月新客分配" autocomplete="off" />
          </label>
          <p>系统会创建表格、检查字段，并在通过后切换当前分配表。无需填写表格 ID。</p>
          <p v-if="createError" class="admin-message error">{{ createError }}</p>
          <div class="ops-row-actions">
            <button class="secondary small" type="button" :disabled="creating" @click="refresh">刷新</button>
            <button class="primary small" type="submit" :disabled="creating || !draftName">{{ creating ? '正在创建…' : '创建并切换' }}</button>
          </div>
        </form>

        <div v-if="history.length" class="assignment-table-manager-history">
          <div>
            <strong>历史分配表</strong>
            <small>保留旧表，不会影响当前分配。</small>
          </div>
          <article v-for="table in history" :key="table.id">
            <div>
              <strong>{{ table.tableName }}</strong>
              <span>{{ table.monthKey }} · {{ statusLabel(table.status) }}</span>
              <small v-if="table.status === 'FAILED' && table.errorMessage" class="assignment-table-manager-failure">{{ table.errorMessage }}</small>
            </div>
            <div class="assignment-table-manager-history-actions">
              <button class="link-button" type="button" :disabled="!table.documentUrl || busy" @click="openTable(table)">打开</button>
              <button class="link-button" type="button" :disabled="!canRebind(table) || busy" :title="canRebind(table) ? '将这张历史表设为当前分配表' : '这条记录没有完整绑定信息，不能换绑'" @click="rebindTable(table)">换绑</button>
              <button class="link-button danger-text" type="button" :disabled="busy" @click="deleteTable(table)">删除</button>
            </div>
          </article>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { openAssignmentTable } from '../../shared/desktopBridge';
import {
  createAssignmentTable,
  deleteAssignmentTable,
  loadAssignmentTables,
  rebindAssignmentTable,
  type AssignmentTable
} from '../../shared/assignmentTableStore';

const emit = defineEmits<{ close: []; changed: [] }>();

const tables = ref<AssignmentTable[]>([]);
const loading = ref(false);
const creating = ref(false);
const opening = ref(false);
const loadError = ref('');
const createError = ref('');
const draftName = ref(defaultName());
const updatingId = ref<number | null>(null);

const activeTable = computed(() => tables.value.find((table) => table.status === 'ACTIVE') ?? null);
const history = computed(() => tables.value.filter((table) => table.id !== activeTable.value?.id).slice(0, 12));
const busy = computed(() => creating.value || opening.value || updatingId.value !== null);

onMounted(() => { void refresh(); });

async function refresh(): Promise<void> {
  if (creating.value) return;
  loading.value = true;
  loadError.value = '';
  try {
    tables.value = await loadAssignmentTables();
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '分配表加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

async function submitCreate(): Promise<void> {
  if (!draftName.value || creating.value) return;
  creating.value = true;
  createError.value = '';
  try {
    const created = await createAssignmentTable(draftName.value);
    tables.value = [created, ...tables.value
      .filter((table) => table.id !== created.id)
      .map((table) => table.status === 'ACTIVE' ? { ...table, status: 'ARCHIVED' as const } : table)];
    draftName.value = defaultName();
    emit('changed');
  } catch (cause) {
    createError.value = cause instanceof Error ? cause.message : '分配表创建失败，请稍后重试';
    await refreshAfterFailure();
  } finally {
    creating.value = false;
  }
}

async function refreshAfterFailure(): Promise<void> {
  try {
    tables.value = await loadAssignmentTables();
  } catch {
    // Keep the actionable creation error visible when the follow-up refresh also fails.
  }
}

async function openTable(table: AssignmentTable): Promise<void> {
  if (!table.documentUrl || opening.value) return;
  opening.value = true;
  try {
    const result = await openAssignmentTable(table.documentUrl);
    if (!result.success) createError.value = result.message || '无法打开企业微信表格';
  } finally {
    opening.value = false;
  }
}

async function rebindTable(table: AssignmentTable): Promise<void> {
  if (!canRebind(table) || busy.value) return;
  if (!window.confirm(`确认换绑到「${table.tableName}」吗？当前分配表会保留为历史表，后续客资将使用这张表。`)) return;
  updatingId.value = table.id;
  createError.value = '';
  try {
    const rebound = await rebindAssignmentTable(table.id);
    tables.value = [rebound, ...tables.value
      .filter((item) => item.id !== rebound.id)
      .map((item) => item.status === 'ACTIVE' ? { ...item, status: 'ARCHIVED' as const } : item)];
    emit('changed');
  } catch (cause) {
    createError.value = cause instanceof Error ? cause.message : '分配表换绑失败，请稍后重试';
  } finally {
    updatingId.value = null;
  }
}

async function deleteTable(table: AssignmentTable): Promise<void> {
  if (busy.value) return;
  if (!window.confirm(`确认删除「${table.tableName}」这条无效历史记录吗？只会移除系统记录，不会删除企业微信中的表格。`)) return;
  updatingId.value = table.id;
  createError.value = '';
  try {
    await deleteAssignmentTable(table.id);
    tables.value = tables.value.filter((item) => item.id !== table.id);
  } catch (cause) {
    createError.value = cause instanceof Error ? cause.message : '分配表删除失败，请稍后重试';
  } finally {
    updatingId.value = null;
  }
}

function canRebind(table: AssignmentTable): boolean {
  return table.status === 'ARCHIVED' && Boolean(table.documentUrl);
}

function close(): void {
  if (!busy.value) emit('close');
}

function defaultName(): string {
  const now = new Date();
  return `${now.getMonth() + 1}月新客分配`;
}

function statusLabel(status: string): string {
  if (status === 'ACTIVE') return '当前使用';
  if (status === 'FAILED') return '创建失败';
  if (status === 'CREATING') return '创建中';
  return '历史表';
}
</script>
