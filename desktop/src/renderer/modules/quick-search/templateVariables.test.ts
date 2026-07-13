import { describe, expect, it } from 'vitest';
import { QUICK_SEARCH_TEMPLATE_VARIABLES, resolveQuickSearchTemplate } from './templateVariables';

describe('quick-search template variables', () => {
  it('uses Chinese placeholders for every variable shown to administrators', () => {
    expect(QUICK_SEARCH_TEMPLATE_VARIABLES.map((item) => item.placeholder)).toEqual([
      '{{客户昵称}}',
      '{{手机号}}',
      '{{意向门店}}',
      '{{意向项目}}',
      '{{客户阶段}}',
      '{{意向等级}}',
      '{{下次跟进时间}}',
      '{{预约日期}}',
      '{{预约项目}}',
      '{{预约门店}}',
      '{{是否到店}}',
      '{{分配管家}}'
    ]);
  });

  it('resolves new Chinese placeholders and legacy English or single-brace placeholders', () => {
    const customer = {
      nickname: '王女士',
      intendedStore: '万江店',
      intentLevel: 'HIGH',
      appointmentDate: '2026-07-20',
      assignedKeeper: '林泽'
    };
    const content = '{{客户昵称}} {{nickname}} {{意向门店}} {{intentLevel}} {预约时间} {管家名} {手机后4位}';

    expect(resolveQuickSearchTemplate(content, customer, '13800001111')).toBe(
      '王女士 王女士 万江店 HIGH 2026-07-20 林泽 1111'
    );
  });

  it('keeps unresolved and unknown placeholders visible instead of deleting template text', () => {
    expect(resolveQuickSearchTemplate('{{预约项目}} {{未知变量}}', {}, '')).toBe('{{预约项目}} {{未知变量}}');
  });
});
