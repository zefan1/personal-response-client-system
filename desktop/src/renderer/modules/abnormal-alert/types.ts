export type AlertType = 'CUSTOMER_COMPLAINT' | 'CHURN_RISK';
export type AlertLevel = 'ERROR' | 'WARN' | 'INFO';

export type AbnormalAlertInboundPayload = {
  phone?: string;
  alertType?: string;
  message?: string;
  level?: string;
  occurredAt?: string;
};

export type AbnormalAlert = {
  alertId: string;
  phone: string;
  alertType: AlertType;
  message: string;
  level: AlertLevel;
  occurredAt: string;
  acknowledged: boolean;
  acknowledgedAt: string | null;
};
