-- Keep existing deliberate custom schedules intact while moving the historical 30-minute default
-- to the production inbound-sync baseline.
UPDATE system_configs
SET config_value = '0 * * * * *',
    description = '模块A定时同步频率（默认每分钟检查企业微信智能表格变化）'
WHERE config_key = 'cache.sync_cron'
  AND config_value = '0 */30 * * * *';
