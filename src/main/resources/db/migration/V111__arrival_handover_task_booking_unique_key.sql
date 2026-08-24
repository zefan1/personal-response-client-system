ALTER TABLE arrival_handover_tasks
  MODIFY appointment_time VARCHAR(30) NOT NULL DEFAULT '',
  MODIFY appointment_item VARCHAR(200) NOT NULL DEFAULT '',
  ADD UNIQUE KEY uk_arrival_handover_booking (phone, appointment_date, appointment_time, appointment_store, appointment_item);
