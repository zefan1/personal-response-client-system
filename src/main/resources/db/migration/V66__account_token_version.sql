ALTER TABLE accounts
  ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 COMMENT 'increments to invalidate issued access tokens' AFTER is_enabled;

UPDATE accounts SET token_version = 0 WHERE token_version IS NULL;
