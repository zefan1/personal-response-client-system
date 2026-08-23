import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import CustomerProfilePanel from './CustomerProfilePanel.vue';
import { customerProfileState, cleanupCustomerProfileStore } from './customerProfileStore';

function resetProfileState(): void {
  cleanupCustomerProfileStore();
  customerProfileState.profile = null;
  customerProfileState.editMode = false;
  customerProfileState.editFields = {};
  customerProfileState.toast = '';
}

describe('CustomerProfilePanel nickname binding', () => {
  afterEach(() => {
    resetProfileState();
    document.body.replaceChildren();
  });

  it('lets an employee fill in the WeChat nickname for an existing new-lead profile', async () => {
    customerProfileState.profile = {
      phoneFull: '13800000000',
      customer: {
        phone: '13800000000',
        phoneFull: '13800000000',
        nickname: '',
        version: 3,
        sourceTable: 'new-leads',
        sourceRowId: 'row-42'
      },
      pendingSuggestions: [],
      currentTags: [],
      tagLocks: [],
      editableTagCategories: []
    };

    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(CustomerProfilePanel);
    app.mount(host);
    await nextTick();

    const editButton = [...host.querySelectorAll('button')]
      .find((button) => button.textContent?.trim() === '编辑档案') as HTMLButtonElement;
    editButton.click();
    await nextTick();

    const nicknameInput = host.querySelector('.profile-summary input') as HTMLInputElement | null;
    expect(nicknameInput).not.toBeNull();
    expect(nicknameInput?.value).toBe('');

    nicknameInput!.value = '小雨';
    nicknameInput!.dispatchEvent(new Event('input'));
    expect(customerProfileState.editFields.nickname).toBe('小雨');

    app.unmount();
  });

  it('shows the complete body profile and hides write actions in read-only mode', async () => {
    customerProfileState.profile = {
      phoneFull: '13800000000',
      customer: {
        id: 11,
        phone: '13800000000',
        nickname: '小雨',
        customerStage: '跟进中',
        lochiaPeriod: '已结束，月经未恢复',
        pregnancyWeight: 62,
        currentWeight: 56,
        diastasisRecti: '两指',
        urineLeakage: '偶有',
        pubicLumbago: '久坐腰痛',
        prevRepairExp: '未做过修复',
        postpartumCheck: '42 天已检查',
        version: 3
      },
      pendingSuggestions: [{ fieldName: 'bodyConcerns', currentValue: '', suggestedValue: '腹部松弛' }],
      currentTags: [],
      tagLocks: [],
      editableTagCategories: []
    };

    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(CustomerProfilePanel, { readOnly: true, embedded: true });
    app.mount(host);
    await nextTick();

    expect(host.textContent).toContain('恶露/月经');
    expect(host.textContent).toContain('孕期体重');
    expect(host.textContent).toContain('腹直肌');
    expect(host.textContent).toContain('漏尿');
    expect(host.textContent).toContain('腰痛/耻骨痛');
    expect(host.textContent).toContain('修复经历');
    expect(host.textContent).toContain('产后检查');
    expect(host.textContent).not.toContain('编辑档案');
    expect(host.textContent).not.toContain('同意并执行');

    app.unmount();
  });

  it('puts the customer overview before detailed profile fields', async () => {
    customerProfileState.profile = {
      phoneFull: '13800000000',
      customer: {
        id: 12,
        phone: '13800000000',
        nickname: '小雨',
        customerStage: '跟进中',
        intendedProject: '产后修复',
        intendedStore: '万江店',
        intentLevel: '高意向',
        nextFollowupDir: '确认到店前提醒',
        version: 3
      },
      latestCommunicationSummary: {
        id: 1,
        customerId: 12,
        versionNo: 1,
        summaryText: '已了解体验方案，等待确认预约时间。',
        lastMessageId: 1,
        generatedAt: '2026-08-10T10:00:00'
      },
      pendingSuggestions: [],
      currentTags: [],
      tagLocks: [],
      editableTagCategories: []
    };

    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(CustomerProfilePanel);
    app.mount(host);
    await nextTick();

    const text = host.textContent || '';
    expect(text).toContain('第一眼总览');
    expect(text).toContain('客户现在想要什么');
    expect(text).toContain('最近到店安排');
    expect(text).toContain('下一步要做什么');
    expect(text).toContain('客户快速了解');
    expect(text).toContain('最近沟通与提醒');
    expect(text.indexOf('第一眼总览')).toBeLessThan(text.indexOf('身份与归属'));

    app.unmount();
  });

  it('groups detailed fields into the communication, body, follow-up and appointment modules', async () => {
    customerProfileState.profile = {
      phoneFull: '13800000000',
      customer: {
        id: 13,
        phone: '13800000000',
        nickname: '小雨',
        intendedStore: '万江店',
        intendedProject: '产后修复',
        postpartumMonths: 8,
        deliveryMethod: '顺产',
        bodyConcerns: '腹直肌、腰部不适',
        followupNotes: '已确认体验内容，等待预约。',
        appointmentDate: '2026-08-12',
        appointmentStore: '万江店',
        version: 3
      },
      pendingSuggestions: [],
      currentTags: [],
      tagLocks: [],
      editableTagCategories: []
    };

    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(CustomerProfilePanel);
    app.mount(host);
    await nextTick();

    const summaries = [...host.querySelectorAll('.profile-card > details.profile-detail-module > summary')]
      .map((summary) => summary.textContent?.replace(/\s+/g, ' ').trim());

    expect(summaries).toEqual(expect.arrayContaining([
      expect.stringContaining('最新沟通汇总'),
      expect.stringContaining('身份与归属'),
      expect.stringContaining('意向与购买'),
      expect.stringContaining('身体情况与沟通偏好'),
      expect.stringContaining('跟进历史、客户标签与 AI 建议'),
      expect.stringContaining('预约、到店与服务资料')
    ]));
    expect(host.querySelector('.profile-detail-module-identity .profile-field-rows')?.textContent).toContain('微信昵称');

    const bodyModule = host.querySelector('.profile-detail-module-body') as HTMLDetailsElement | null;
    expect(bodyModule?.open).toBe(false);
    bodyModule?.querySelector('summary')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(bodyModule?.open).toBe(true);

    app.unmount();
  });

  it('shows a copy button for a complete phone in the profile overview', async () => {
    customerProfileState.profile = {
      phoneFull: '13800000000',
      customer: {
        phone: '138****0000',
        phoneFull: '13800000000',
        nickname: '小雨',
        version: 3
      },
      pendingSuggestions: [],
      currentTags: [],
      tagLocks: [],
      editableTagCategories: []
    };

    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(CustomerProfilePanel);
    app.mount(host);
    await nextTick();

    const copyButton = host.querySelector('button[aria-label="复制客户手机号"]');
    expect(copyButton).not.toBeNull();
    expect(copyButton?.getAttribute('title')).toBe('复制客户手机号');

    app.unmount();
  });
});
