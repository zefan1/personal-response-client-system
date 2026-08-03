import { createApp, h, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import ReplyTaskDrawer from './ReplyTaskDrawer.vue';

type TaskItem = {
  sessionId: string;
  nickname: string;
  status: string;
  updatedAt: number;
  jobId?: string;
  archived?: boolean;
};

async function mountDrawer(tasks: TaskItem[]) {
  const selected: string[] = [];
  const cancelled: Array<{ jobId: string; sessionId: string }> = [];
  const cleared = { value: 0 };
  const closed = { value: 0 };
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp({
    render: () => h(ReplyTaskDrawer, {
      open: true,
      tasks,
      activeSessionId: 'reply-2',
      onSelect: (sessionId: string) => selected.push(sessionId),
      onClose: () => { closed.value += 1; },
      onClear: () => { cleared.value += 1; },
      onCancel: (jobId: string, sessionId: string) => cancelled.push({ jobId, sessionId })
    })
  });
  app.mount(host);
  await nextTick();
  return { app, host, selected, closed, cleared, cancelled };
}

describe('ReplyTaskDrawer', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('renders no more than the recent thirty tasks and restores a task by selection', async () => {
    const tasks = Array.from({ length: 35 }, (_, index) => ({
      sessionId: `reply-${index + 1}`,
      nickname: `客户${index + 1}`,
      status: index === 0 ? 'QUEUED' : 'READY',
      updatedAt: 1_700_000_000_000 - index,
      archived: index === 1
    }));
    const { app, host, selected } = await mountDrawer(tasks);

    expect(host.querySelectorAll('[data-testid^="reply-task-drawer-row-"]')).toHaveLength(30);
    expect(host.querySelector('[data-testid="reply-task-drawer-row-reply-2"]')?.textContent).toContain('已暂存');
    (host.querySelector('[data-testid="reply-task-drawer-row-reply-1"]') as HTMLButtonElement).click();
    await nextTick();

    expect(selected).toEqual(['reply-1']);
    app.unmount();
  });

  it('emits clear and cancels an active recognition job', async () => {
    const { app, host, cleared, cancelled } = await mountDrawer([
      { sessionId: 'reply-1', nickname: 'Alice', status: 'QUEUED', jobId: 'job-1', updatedAt: 1_700_000_000_000 }
    ]);

    (host.querySelector('[data-testid="archive-reply-tasks"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="cancel-reply-task-job-1"]') as HTMLButtonElement).click();
    await nextTick();

    expect(cleared.value).toBe(1);
    expect(cancelled).toEqual([{ jobId: 'job-1', sessionId: 'reply-1' }]);
    app.unmount();
  });

  it('does not present retired customer-selection work as an actionable task', async () => {
    const { app, host } = await mountDrawer([
      { sessionId: 'legacy-selection', nickname: 'Alice', status: 'MULTIPLE', updatedAt: 1 }
    ]);

    expect(host.querySelector('[data-testid="reply-task-drawer-row-legacy-selection"]')?.textContent)
      .not.toContain('待选择');
    app.unmount();
  });
});
