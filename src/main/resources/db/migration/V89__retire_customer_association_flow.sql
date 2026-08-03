UPDATE communication_recognition_batches
SET association_status = 'LEGACY_UNASSIGNED'
WHERE association_status = 'PENDING';

DROP TABLE IF EXISTS communication_pending_task_links;
DROP TABLE IF EXISTS communication_platform_identities;

ALTER TABLE communication_recognition_batches
  DROP INDEX idx_communication_batch_owner_status,
  DROP COLUMN association_status;
