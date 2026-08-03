type PhoneSummary = {
  phone: string;
  phoneFull?: string | null;
};

type ProfileWithCustomer = {
  phoneFull?: string | null;
  customer: PhoneSummary;
};

export function summaryPhone(customer: PhoneSummary): string {
  return customer.phoneFull || customer.phone;
}

export function candidatePhone(customer: PhoneSummary): string {
  return normalizeFullPhone(customer.phoneFull) || normalizeFullPhone(customer.phone);
}

export function normalizeFullPhone(value?: string | null): string {
  if (!value || /[*xX]/.test(value)) return '';
  const digits = value.replace(/\D/g, '');
  const normalized = digits.length === 13 && digits.startsWith('86') ? digits.slice(2) : digits;
  return /^\d{11}$/.test(normalized) ? normalized : '';
}

export function isSameFullPhone(left: string, right: string): boolean {
  const normalizedLeft = normalizeFullPhone(left);
  const normalizedRight = normalizeFullPhone(right);
  return Boolean(normalizedLeft && normalizedRight && normalizedLeft === normalizedRight);
}

export function profilePhone(profile: ProfileWithCustomer): string {
  return profile.phoneFull || profile.customer.phoneFull || profile.customer.phone;
}

export function isSamePhone(left: string, right: string): boolean {
  if (!left || !right) return false;
  return left === right || left.endsWith(right.slice(-4)) || right.endsWith(left.slice(-4));
}
