UPDATE system_configs
SET config_value = '15000',
    description = 'Image recognition timeout ms, range 15000-60000'
WHERE config_key = 'image.timeout_ms'
  AND CAST(config_value AS DECIMAL(10, 0)) < 15000;
