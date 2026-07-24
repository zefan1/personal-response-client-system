CREATE INDEX idx_supervision_events_occurred_at ON supervision_events (occurred_at);
CREATE INDEX idx_llm_call_logs_created_at ON llm_call_logs (created_at);
CREATE INDEX idx_skill_call_logs_created_at ON skill_call_logs (created_at);
CREATE INDEX idx_pending_reply_tasks_status_finished_at ON pending_reply_tasks (status, finished_at);
