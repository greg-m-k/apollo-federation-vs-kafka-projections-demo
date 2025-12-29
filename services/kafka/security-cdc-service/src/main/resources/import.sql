-- Sample Security/BadgeHolder Data for CDC service
INSERT INTO badge_holders (id, person_id, badge_number, access_level, clearance, active) VALUES
('badge-001', 'person-001', 'B10001', 'RESTRICTED', 'SECRET', true),
('badge-002', 'person-002', 'B10002', 'ALL_ACCESS', 'TOP_SECRET', true),
('badge-003', 'person-003', 'B10003', 'STANDARD', 'CONFIDENTIAL', true),
('badge-005', 'person-005', 'B10005', 'RESTRICTED', 'SECRET', true);
