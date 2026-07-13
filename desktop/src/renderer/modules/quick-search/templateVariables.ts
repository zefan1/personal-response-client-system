export type QuickSearchTemplateVariable = {
  key: string;
  label: string;
  placeholder: string;
  aliases: string[];
};

export const QUICK_SEARCH_TEMPLATE_VARIABLES: QuickSearchTemplateVariable[] = [
  variable('nickname', '客户昵称'),
  variable('phone', '手机号'),
  variable('intendedStore', '意向门店'),
  variable('intendedProject', '意向项目'),
  variable('customerStage', '客户阶段'),
  variable('intentLevel', '意向等级'),
  variable('nextFollowupAt', '下次跟进时间'),
  variable('appointmentDate', '预约日期', ['预约时间']),
  variable('appointmentItem', '预约项目'),
  variable('appointmentStore', '预约门店'),
  variable('arrived', '是否到店'),
  variable('assignedKeeper', '分配管家', ['管家名'])
];

const VARIABLE_KEY_BY_ALIAS = new Map<string, string>();
QUICK_SEARCH_TEMPLATE_VARIABLES.forEach((item) => {
  [item.key, item.label, ...item.aliases].forEach((alias) => VARIABLE_KEY_BY_ALIAS.set(alias, item.key));
});

export function resolveQuickSearchTemplate(
  content: string,
  customer: Record<string, unknown>,
  phoneFull = ''
): string {
  return content
    .replace(/\{\{([^{}]+)\}\}/g, (match, token: string) => {
      const key = VARIABLE_KEY_BY_ALIAS.get(token.trim());
      return key ? templateValue(customer, key, phoneFull) || match : match;
    })
    .replace(/\{([^{}]+)\}/g, (match, token: string) => {
      if (token === '手机后4位') {
        return phoneLast4(customer, phoneFull) || match;
      }
      const key = VARIABLE_KEY_BY_ALIAS.get(token.trim());
      return key ? templateValue(customer, key, phoneFull) || match : match;
    });
}

function variable(key: string, label: string, aliases: string[] = []): QuickSearchTemplateVariable {
  return { key, label, placeholder: `{{${label}}}`, aliases };
}

function templateValue(customer: Record<string, unknown>, key: string, phoneFull: string): string {
  if (key === 'phone') {
    return phoneFull || String(customer.phoneFull || customer.phone || '');
  }
  const value = customer[key];
  if (value === undefined || value === null || value === '') {
    return '';
  }
  return String(value);
}

function phoneLast4(customer: Record<string, unknown>, phoneFull: string): string {
  const phone = phoneFull || String(customer.phoneFull || customer.phone || '');
  return phone.length >= 4 ? phone.slice(-4) : '';
}
