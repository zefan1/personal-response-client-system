import { describe, expect, it } from 'vitest';
import { QUICK_SEARCH_TEMPLATE_VARIABLES, resolveQuickSearchTemplate } from './templateVariables';

describe('quick-search template variables', () => {
  it('includes every unique-fact field that an administrator can insert', () => {
    expect(QUICK_SEARCH_TEMPLATE_VARIABLES.map((item) => item.key)).toEqual(expect.arrayContaining([
      'customerName', 'bodyConcerns', 'followupNotes', 'appointmentStatus',
      'arrivalExperienceProject', 'customerStage', 'transactionPrimaryReason'
    ]));
  });

  it('resolves Chinese, legacy English, and unique-fact field-key placeholders', () => {
    const customer = {
      nickname: '王女士',
      customerName: '王小雨',
      intendedStore: '万江店',
      intentLevel: 'HIGH',
      appointmentDate: '2026-07-20',
      appointmentTime: '14:30',
      assignedKeeper: '林泽'
    };
    const content = '{{客户昵称}} {{nickname}} {{customerName}} {{意向门店}} {{intentLevel}} {预约时间} {管家名} {手机后4位}';

    expect(resolveQuickSearchTemplate(content, customer, '13800001111')).toBe(
      '王女士 王女士 王小雨 万江店 HIGH 14:30 林泽 1111'
    );
  });

  it('keeps unresolved and unknown placeholders visible instead of deleting template text', () => {
    expect(resolveQuickSearchTemplate('{{预约项目}} {{未知变量}}', {}, '')).toBe('{{预约项目}} {{未知变量}}');
  });

  it('resolves appointment time and customer name from the appointment form context', () => {
    const customer = {
      customerName: '王小雨',
      appointmentDateTime: '2026-08-26T14:00'
    };

    expect(resolveQuickSearchTemplate('客户名称：{{客户名称}}\n预约时间：{{预约时间}}', customer)).toBe(
      '客户名称：王小雨\n预约时间：14:00'
    );
  });

  it('resolves Chinese labels for unique-fact fields outside the original shortcut list', () => {
    expect(resolveQuickSearchTemplate('{{客户关注点}} / {{身体关注}} / {{跟进记录}}', {
      bodyConcerns: '腹直肌分离',
      followupNotes: '已约到店评估'
    })).toBe('腹直肌分离 / 腹直肌分离 / 已约到店评估');
  });

  it('keeps legacy project placeholders compatible with their current field names', () => {
    expect(resolveQuickSearchTemplate('{{项目类型}} {{体验项目}}', {
      projectType: '体验卡',
      experienceProject: '盆底肌评估'
    })).toBe('体验卡 盆底肌评估');
  });

  it('prefers an edited appointmentDateTime over a stale separate time value', () => {
    expect(resolveQuickSearchTemplate('{{预约时间}}', {
      appointmentDateTime: '2026-08-26T16:30',
      appointmentTime: '14:00'
    })).toBe('16:30');
  });
});
