DELETE target
FROM skill_scene_bindings target
JOIN skill_scene_bindings keeper
  ON keeper.skill_id = target.skill_id
 AND keeper.scene = target.scene
 AND keeper.lead_type = target.lead_type
 AND (
   keeper.enabled > target.enabled
   OR (keeper.enabled = target.enabled AND keeper.priority < target.priority)
   OR (keeper.enabled = target.enabled AND keeper.priority = target.priority AND keeper.updated_at > target.updated_at)
   OR (keeper.enabled = target.enabled AND keeper.priority = target.priority AND keeper.updated_at = target.updated_at AND keeper.id < target.id)
 )
WHERE target.id <> keeper.id;

DELETE target
FROM datasource_mapping_versions target
JOIN datasource_mapping_versions keeper
  ON keeper.datasource_id = target.datasource_id
 AND keeper.version = target.version
 AND (
   keeper.created_at > target.created_at
   OR (keeper.created_at = target.created_at AND keeper.id < target.id)
 )
WHERE target.id <> keeper.id;

DELETE target
FROM skill_prompt_versions target
JOIN skill_prompt_versions keeper
  ON keeper.config_key = target.config_key
 AND keeper.version = target.version
 AND (
   keeper.is_stable > target.is_stable
   OR (keeper.is_stable = target.is_stable AND keeper.created_at > target.created_at)
   OR (keeper.is_stable = target.is_stable AND keeper.created_at = target.created_at AND keeper.id < target.id)
 )
WHERE target.id <> keeper.id;

ALTER TABLE skill_scene_bindings
  ADD UNIQUE INDEX uk_skill_scene_lead (skill_id, scene, lead_type);

ALTER TABLE datasource_mapping_versions
  ADD UNIQUE INDEX uk_datasource_version (datasource_id, version);

ALTER TABLE skill_prompt_versions
  ADD UNIQUE INDEX uk_prompt_version (config_key, version);

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('quicksearch.storage.public_base_url', '/uploads/quick-search', 'Quick search uploaded image public base URL'),
  ('quicksearch.storage.root', 'uploads/quick-search', 'Quick search uploaded image local storage root')
ON DUPLICATE KEY UPDATE description = VALUES(description);
