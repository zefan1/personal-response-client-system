export type QuickSearchTemplateVariable = {
  key: string;
  label: string;
  placeholder: string;
  aliases: string[];
};

export const QUICK_SEARCH_TEMPLATE_VARIABLES: QuickSearchTemplateVariable[] = [
  variable('nickname', '客户昵称'),
  variable('customerName', '客户名称', ['客户姓名', '真实姓名']),
  variable('phone', '手机号'),
  variable('intendedStore', '意向门店'),
  variable('intendedProject', '意向项目'),
  variable('customerStage', '客户阶段'),
  variable('intentLevel', '意向等级'),
  variable('nextFollowupAt', '下次跟进时间'),
  variable('appointmentDate', '预约日期'),
  variable('appointmentTime', '预约时间'),
  variable('appointmentItem', '预约项目'),
  variable('appointmentStore', '预约门店'),
  variable('visitType', '类型'),
  variable('voucherRedeemed', '是否核券'),
  variable('experienceProject', '体验项目'),
  variable('projectType', '项目类型'),
  variable('historicalExperienceCount', '历史体验次数'),
  variable('customerReport', '客户报告'),
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
      const key = templateKey(token, customer);
      return key ? templateValue(customer, key, phoneFull) || match : match;
    })
    .replace(/\{([^{}]+)\}/g, (match, token: string) => {
      if (token === '手机后4位') {
        return phoneLast4(customer, phoneFull) || match;
      }
      const key = templateKey(token, customer);
      return key ? templateValue(customer, key, phoneFull) || match : match;
    });
}

function templateKey(token: string, customer: Record<string, unknown>): string | undefined {
  const normalized = token.trim();
  return VARIABLE_KEY_BY_ALIAS.get(normalized)
    ?? (Object.prototype.hasOwnProperty.call(customer, normalized) ? normalized : undefined);
}

function variable(key: string, label: string, aliases: string[] = []): QuickSearchTemplateVariable {
  return { key, label, placeholder: `{{${label}}}`, aliases };
}

function templateValue(customer: Record<string, unknown>, key: string, phoneFull: string): string {
  if (key === 'phone') {
    return phoneFull || String(customer.phoneFull || customer.phone || '');
  }
  if (key === 'appointmentDate') {
    const dateTime = String(customer.appointmentDateTime ?? '').trim();
    if (dateTime) return dateTime.split('T')[0].split(' ')[0];
  }
  if (key === 'appointmentTime') {
    // The appointment form may expose one canonical date-time field instead
    // of separate date/time fields. Prefer it so edited values are copied.
    const dateTime = String(customer.appointmentDateTime ?? '').trim();
    if (dateTime) {
      const separator = dateTime.includes('T') ? 'T' : ' ';
      const time = dateTime.split(separator)[1] || '';
      return time.slice(0, 5);
    }
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
