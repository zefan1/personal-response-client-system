-- Manual appointment records may be created before every appointment field is known.
ALTER TABLE arrival_handover_tasks
  MODIFY appointment_date DATE NULL,
  MODIFY appointment_store VARCHAR(100) NULL;

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'arrival.appointment_success_template_id', '', 'Enabled quick-search template ID copied after a manual appointment is saved'
WHERE NOT EXISTS (
  SELECT 1 FROM system_configs WHERE config_key = 'arrival.appointment_success_template_id'
);
