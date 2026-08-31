ALTER TABLE skill_scene_bindings
  ADD COLUMN skill_base_url VARCHAR(500) NULL COMMENT 'Scene Skill endpoint',
  ADD COLUMN skill_api_key VARCHAR(1000) NULL COMMENT 'Encrypted Scene Skill API key',
  ADD COLUMN skill_api_key_last4 VARCHAR(4) NULL COMMENT 'Scene Skill API key suffix',
  ADD COLUMN skill_protocol VARCHAR(50) NULL COMMENT 'Scene Skill transport protocol';

UPDATE skill_scene_bindings binding
JOIN skill_environments environment ON environment.is_active = 1
SET binding.skill_base_url = environment.base_url,
    binding.skill_api_key = environment.api_key,
    binding.skill_api_key_last4 = environment.api_key_last4,
    binding.skill_protocol = environment.protocol
WHERE binding.skill_base_url IS NULL
  AND environment.provider = 'skill';
