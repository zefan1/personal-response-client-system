import { deleteJson, getJson, postJson } from './apiClient';

const CREATE_TIMEOUT_MS = 120_000;

export type AssignmentTable = {
  id: number;
  tableName: string;
  monthKey: string;
  documentUrl: string;
  status: 'CREATING' | 'READY' | 'ACTIVE' | 'ARCHIVED' | 'FAILED' | string;
  errorMessage?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  activatedAt?: string | null;
};

export async function loadAssignmentTables(): Promise<AssignmentTable[]> {
  const response = await getJson<AssignmentTable[]>('/api/v1/assignment-tables');
  if (!response.success || !response.data) {
    throw new Error(response.message || '分配表列表加载失败');
  }
  return response.data;
}

export async function createAssignmentTable(tableName: string): Promise<AssignmentTable> {
  const response = await postJson<AssignmentTable>(
    '/api/v1/assignment-tables',
    { tableName },
    CREATE_TIMEOUT_MS
  );
  if (!response.success || !response.data) {
    throw new Error(response.message || '分配表创建失败');
  }
  return response.data;
}

export async function rebindAssignmentTable(id: number): Promise<AssignmentTable> {
  const response = await postJson<AssignmentTable>(`/api/v1/assignment-tables/${id}/rebind`, {});
  if (!response.success || !response.data) {
    throw new Error(response.message || '分配表换绑失败');
  }
  return response.data;
}

export async function deleteAssignmentTable(id: number): Promise<void> {
  const response = await deleteJson<unknown>(`/api/v1/assignment-tables/${id}`);
  if (!response.success) {
    throw new Error(response.message || '分配表删除失败');
  }
}
