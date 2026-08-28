export type QuickSearchTemplateVariable = {
  key: string;
  label: string;
  placeholder: string;
  aliases: string[];
};

export const QUICK_SEARCH_TEMPLATE_VARIABLES: QuickSearchTemplateVariable[] = [
  variable('customerName', '客户姓名', ['客户名称', '真实姓名']),
  variable('nickname', '客户昵称'),
  variable('phone', '手机号'),
  variable('wechatId', '微信号'),
  variable('sourceChannel', '来源渠道'),
  variable('leadType', '客资类型'),
  variable('leadCaptureMethod', '留资方式'),
  variable('platformLeadAt', '平台留资时间'),
  variable('advertisingType', '广告类型'),
  variable('globalAdvertisementId', '全域广告ID'),
  variable('standardAdvertisementId', '标准广告ID'),
  variable('contentId', '内容ID'),
  variable('videoId', '视频ID'),
  variable('orderNumber', '订单号'),
  variable('conversionTrace', '转化追溯'),
  variable('previousAssignedKeeper', '上次分配人'),
  variable('previousPlatformLeadAt', '上次留资时间'),
  variable('assignedKeeper', '分配管家', ['管家名']),
  variable('assignedAt', '分配日期'),
  variable('assignmentMonth', '分配月份'),
  variable('intendedStore', '意向门店'),
  variable('intendedProject', '意向项目'),
  variable('purchasedProject', '已购项目'),
  variable('experienceCardType', '体验卡类型'),
  variable('pendingOrderStatus', '挂单情况'),
  variable('purchaseDate', '购卡时间'),
  variable('customerLevel', '客户等级'),
  variable('intentLevel', '意向等级'),
  variable('personalityType', '性格类型'),
  variable('postpartumMonths', '产后月份'),
  variable('parity', '胎次'),
  variable('deliveryMethod', '分娩方式'),
  variable('breastfeeding', '哺乳情况'),
  variable('lochiaPeriod', '恶露/月经情况'),
  variable('pregnancyWeight', '孕期增重'),
  variable('currentWeight', '当前体重'),
  variable('bodyConcerns', '客户关注点', ['身体关注']),
  variable('diastasisRecti', '腹直肌分离'),
  variable('urineLeakage', '漏尿情况'),
  variable('pubicLumbago', '耻骨/腰痛'),
  variable('prevRepairExp', '既往修复经历'),
  variable('postpartumCheck', '产后检查'),
  variable('exerciseHabits', '运动习惯'),
  variable('customerProfileSummary', '客户档案摘要'),
  variable('internalNote', '备注'),
  variable('firstTrackingCapture', '第一次追踪捕捉'),
  variable('secondTrackingCapture', '第二次追踪捕捉'),
  variable('thirdTrackingCapture', '第三次追踪捕捉'),
  variable('lastFollowupAt', '最近跟进时间'),
  variable('followupNotes', '跟进记录'),
  variable('nextFollowupAt', '下次跟进时间'),
  variable('nextFollowupDir', '下次跟进方向'),
  variable('appointmentDate', '预约日期'),
  variable('appointmentTime', '预约时间'),
  variable('appointmentItem', '预约项目'),
  variable('appointmentStore', '预约门店'),
  variable('appointmentStatus', '预约状态'),
  variable('arrived', '是否到店'),
  variable('arrivalHandoverRecord', '到店衔接记录'),
  variable('arrivalProjectType', '项目类型'),
  variable('arrivalExperienceProject', '到店体验项目', ['体验项目']),
  variable('historicalExperienceCount', '历史体验次数'),
  variable('customerReport', '客户报告'),
  variable('receptionTeacher', '接待老师'),
  variable('receptionConsultant', '接待顾问'),
  variable('voucherRedeemed', '是否核券'),
  variable('customerStage', '客户阶段'),
  variable('transactionAmount', '成交金额'),
  variable('transactionAt', '成交时间'),
  variable('transactionPrimaryReason', '成交主因')
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
  const value = customer[key] ?? legacyTemplateValue(customer, key);
  if (value === undefined || value === null || value === '') {
    return '';
  }
  return String(value);
}

function legacyTemplateValue(customer: Record<string, unknown>, key: string): unknown {
  const legacyKey = ({
    arrivalProjectType: 'projectType',
    arrivalExperienceProject: 'experienceProject',
    leadType: 'visitType'
  } as Record<string, string>)[key];
  return legacyKey ? customer[legacyKey] : undefined;
}

function phoneLast4(customer: Record<string, unknown>, phoneFull: string): string {
  const phone = phoneFull || String(customer.phoneFull || customer.phone || '');
  return phone.length >= 4 ? phone.slice(-4) : '';
}
