UPDATE supervision_events
SET event_type = 'RECOGNITION_PROCESSED'
WHERE event_type = 'PENDING_ENTERED';

UPDATE supervision_monthly_metric_snapshots
SET metric_key = 'AI_REPLY_CONVERSION'
WHERE metric_key = 'AI_ASSOCIATED_CONVERSION';
