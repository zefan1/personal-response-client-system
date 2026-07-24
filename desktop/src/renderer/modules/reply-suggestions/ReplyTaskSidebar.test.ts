import { createApp, h, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import ReplyTaskSidebar from './ReplyTaskSidebar.vue';

type TaskItem = {
  sessionId: string;
  nickname: string;
  status: string;
};

async function mountSidebar(tasks: TaskItem[]) {
  const selected: string[] = [];
  const openAll = { value: 0 };
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp({
    render: () => h(ReplyTaskSidebar, {
      tasks,
      activeSessionId: 'reply-2',
      onSelect: (sessionId: string) => selected.push(sessionId),
      onOpenAll: () => { openAll.value += 1; }
    })
  });
  app.mount(host);
  await nextTick();
  return { app, host, selected, openAll };
}

describe('ReplyTaskSidebar', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('shows only task nickname and status in the compact sidebar', async () => {
    const { app, host } = await mountSidebar([
      { sessionId: 'reply-1', nickname: 'Alice', status: 'QUEUED' },
      { sessionId: 'reply-2', nickname: 'Betty', status: 'READY' }
    ]);

    expect(host.querySelectorAll('[data-testid^="reply-task-row-"]')).toHaveLength(2);
    expect(host.querySelector('[data-testid="reply-task-row-reply-1"]')?.textContent).toContain('Alice');
    expect(host.querySelector('[data-testid="reply-task-row-reply-1"]')?.textContent).toContain('排队中');
    expect(host.querySelector('[data-testid="reply-task-row-reply-1"]')?.textContent).not.toContain('****');
    expect(host.querySelector('[data-testid="reply-task-row-reply-2"]')?.classList).toContain('active');
    app.unmount();
  });

  it('opens the selected task and the full task list', async () => {
    const { app, host, selected, openAll } = await mountSidebar([
      { sessionId: 'reply-1', nickname: 'Alice', status: 'QUEUED' }
    ]);

    (host.querySelector('[data-testid="reply-task-row-reply-1"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="open-reply-task-drawer"]') as HTMLButtonElement).click();
    await nextTick();

    expect(selected).toEqual(['reply-1']);
    expect(openAll.value).toBe(1);
    app.unmount();
  });
});
