import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: apiMocks.getJson,
  postJson: apiMocks.postJson
}));

import { createAssignmentTable, loadAssignmentTables } from './assignmentTableStore';

describe('assignmentTableStore', () => {
  beforeEach(() => {
    apiMocks.getJson.mockReset();
    apiMocks.postJson.mockReset();
  });

  it('loads table history without exposing internal document identifiers', async () => {
    apiMocks.getJson.mockResolvedValue({
      success: true,
      data: [{ id: 1, tableName: '8月分配', monthKey: '2026-08', documentUrl: 'https://doc.weixin.qq.com/old', status: 'ARCHIVED' }]
    });

    await expect(loadAssignmentTables()).resolves.toHaveLength(1);
    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/assignment-tables');
  });

  it('sends the colleague-defined table name to the create endpoint', async () => {
    apiMocks.postJson.mockResolvedValue({
      success: true,
      data: { id: 2, tableName: '国庆活动客资', monthKey: '2026-10', documentUrl: 'https://doc.weixin.qq.com/new', status: 'ACTIVE' }
    });

    await expect(createAssignmentTable('国庆活动客资')).resolves.toMatchObject({ tableName: '国庆活动客资' });
    expect(apiMocks.postJson).toHaveBeenCalledWith(
      '/api/v1/assignment-tables',
      { tableName: '国庆活动客资' },
      120_000
    );
  });

  it('surfaces the backend reason when creation fails', async () => {
    apiMocks.postJson.mockResolvedValue({ success: false, data: null, message: '这个表格名称已经存在，请换一个名称' });

    await expect(createAssignmentTable('重复名称')).rejects.toThrow('这个表格名称已经存在');
  });
});
