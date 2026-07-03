UPDATE customers
SET lead_type = 'PENDING'
WHERE lead_type IS NOT NULL
  AND lead_type NOT IN ('TUAN_GOU', 'XIAN_SUO', 'PENDING');
