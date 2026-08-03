UPDATE system_configs
SET config_value = 'active',
    description = 'Temporary recognition image subdirectory below the application temporary root'
WHERE config_key = 'chat.recognition_temp_root'
  AND config_value = 'uploads/temporary-recognition';
