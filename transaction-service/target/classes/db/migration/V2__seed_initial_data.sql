INSERT INTO accounts (account_id, holder_name, home_country, home_city, daily_limit, avg_tx_amount, is_blacklisted, risk_tier)
VALUES
    ('ACC-1001', 'Budi Santoso',    'IDN', 'Jakarta',   50000000.00,  750000.00, FALSE, 'STANDARD'),
    ('ACC-1002', 'Siti Rahmawati',  'IDN', 'Bandung',   75000000.00, 1200000.00, FALSE, 'STANDARD'),
    ('ACC-1003', 'Raka Pratama',    'IDN', 'Surabaya',  60000000.00,  900000.00, FALSE, 'STANDARD'),
    ('ACC-1004', 'Dina Kusuma',     'IDN', 'Medan',     40000000.00,  650000.00, FALSE, 'STANDARD'),
    ('ACC-1005', 'Hendra Wijaya',   'IDN', 'Jakarta',  150000000.00, 3500000.00, FALSE, 'PREMIUM'),
    ('ACC-9999', 'Shadow Syndicate','IDN', 'Unknown',   10000000.00,  500000.00, TRUE,  'HIGH_RISK')
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO risk_rules (rule_code, rule_name, category, description, weight, threshold_value, enabled)
VALUES
    ('VELOCITY_SPIKE',        'High-Frequency Velocity Spike',  'BEHAVIORAL', 'Triggers when account exceeds transaction count threshold within 1-minute window', 35, 3.0, TRUE),
    ('AMOUNT_ANOMALY',        'Extreme Amount Deviation',       'MONETARY',   'Triggers when transaction amount exceeds multiple of account historical average', 30, 5.0, TRUE),
    ('GEO_IMPOSSIBLE_TRAVEL', 'Impossible Geographic Travel',   'LOCATION',   'Triggers when travel speed between consecutive transactions exceeds physical limit (km/h)', 35, 900.0, TRUE),
    ('BLACKLIST_ACCOUNT',     'Sanctioned or Blacklisted Entity','COMPLIANCE','Triggers immediately if source or target account is blacklisted', 85, 1.0, TRUE)
ON CONFLICT (rule_code) DO NOTHING;

-- Baseline transaksi normal untuk ACC-1001 (membentuk rata-rata & standar deviasi historis)
INSERT INTO transactions (tx_id, idempotency_key, source_account_id, target_account_id, amount, currency, tx_type, merchant_name, merchant_category, country_code, city, latitude, longitude, device_id, ip_address, status, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111101', 'seed-norm-01', 'ACC-1001', NULL, 450000.00, 'IDR', 'PAYMENT', 'Kopi Nusantara', 'FNB', 'IDN', 'Jakarta', -6.2088, 106.8456, 'DEV-A1', '103.10.1.11', 'APPROVED', NOW() - INTERVAL '10 days'),
    ('11111111-1111-1111-1111-111111111102', 'seed-norm-02', 'ACC-1001', NULL, 620000.00, 'IDR', 'PAYMENT', 'Mart Express', 'GROCERY', 'IDN', 'Jakarta', -6.2146, 106.8451, 'DEV-A1', '103.10.1.11', 'APPROVED', NOW() - INTERVAL '8 days'),
    ('11111111-1111-1111-1111-111111111103', 'seed-norm-03', 'ACC-1001', NULL, 510000.00, 'IDR', 'PAYMENT', 'Cloud Transit', 'TRANSPORT', 'IDN', 'Jakarta', -6.1950, 106.8227, 'DEV-A1', '103.10.1.11', 'APPROVED', NOW() - INTERVAL '6 days'),
    ('11111111-1111-1111-1111-111111111104', 'seed-norm-04', 'ACC-1001', NULL, 780000.00, 'IDR', 'PAYMENT', 'Tech Gadget Hub', 'ELECTRONICS', 'IDN', 'Jakarta', -6.2297, 106.8090, 'DEV-A1', '103.10.1.11', 'APPROVED', NOW() - INTERVAL '4 days'),
    ('11111111-1111-1111-1111-111111111105', 'seed-norm-05', 'ACC-1001', NULL, 590000.00, 'IDR', 'PAYMENT', 'Urban Bistro', 'FNB', 'IDN', 'Jakarta', -6.2241, 106.8095, 'DEV-A1', '103.10.1.11', 'APPROVED', NOW() - INTERVAL '2 days'),
    -- Anomali nominal ekstrem untuk ACC-1001 (akan terdeteksi tinggi di Z-Score Window Function)
    ('11111111-1111-1111-1111-111111111106', 'seed-anom-01', 'ACC-1001', NULL, 18500000.00, 'IDR', 'PAYMENT', 'Luxury Watch Vault', 'LUXURY', 'SGP', 'Singapore', 1.3521, 103.8198, 'DEV-UNKNOWN', '185.220.101.4', 'BLOCKED', NOW() - INTERVAL '3 hours'),

    -- Pola Circular Transfer (Money Laundering Ring): ACC-1002 -> ACC-1003 -> ACC-1004 -> ACC-1002
    ('22222222-2222-2222-2222-222222222201', 'seed-ring-01', 'ACC-1002', 'ACC-1003', 15000000.00, 'IDR', 'TRANSFER', 'Internal Transfer', 'P2P', 'IDN', 'Bandung',  -6.9175, 107.6191, 'DEV-B1', '103.20.2.21', 'APPROVED', NOW() - INTERVAL '50 minutes'),
    ('22222222-2222-2222-2222-222222222202', 'seed-ring-02', 'ACC-1003', 'ACC-1004', 14800000.00, 'IDR', 'TRANSFER', 'Internal Transfer', 'P2P', 'IDN', 'Surabaya', -7.2575, 112.7521, 'DEV-C1', '103.30.3.31', 'APPROVED', NOW() - INTERVAL '35 minutes'),
    ('22222222-2222-2222-2222-222222222203', 'seed-ring-03', 'ACC-1004', 'ACC-1002', 14500000.00, 'IDR', 'TRANSFER', 'Internal Transfer', 'P2P', 'IDN', 'Medan',     3.5952,  98.6722, 'DEV-D1', '103.40.4.41', 'APPROVED', NOW() - INTERVAL '20 minutes')
ON CONFLICT (tx_id) DO NOTHING;