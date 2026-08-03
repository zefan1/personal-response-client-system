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
});
