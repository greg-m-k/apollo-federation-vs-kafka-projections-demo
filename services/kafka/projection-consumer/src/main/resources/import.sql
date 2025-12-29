-- Seed data for CDC Projections (mirrors Federation seed data)
-- This provides initial data so the demo works immediately on startup

-- Person Projections
INSERT INTO person_projections (id, name, email, hire_date, active, last_updated, event_version) VALUES
('person-001', 'Alice Johnson', 'alice.johnson@company.com', '2020-01-15', true, CURRENT_TIMESTAMP, 1),
('person-002', 'Bob Smith', 'bob.smith@company.com', '2019-06-01', true, CURRENT_TIMESTAMP, 1),
('person-003', 'Carol Williams', 'carol.williams@company.com', '2021-03-20', true, CURRENT_TIMESTAMP, 1),
('person-004', 'David Brown', 'david.brown@company.com', '2018-11-10', false, CURRENT_TIMESTAMP, 1),
('person-005', 'Eva Martinez', 'eva.martinez@company.com', '2022-08-05', true, CURRENT_TIMESTAMP, 1);

-- Employee Projections
INSERT INTO employee_projections (id, person_id, title, department, salary, active, last_updated, event_version) VALUES
('emp-001', 'person-001', 'Senior Software Engineer', 'Engineering', 150000.00, true, CURRENT_TIMESTAMP, 1),
('emp-002', 'person-002', 'Product Manager', 'Product', 140000.00, true, CURRENT_TIMESTAMP, 1),
('emp-003', 'person-003', 'Data Analyst', 'Analytics', 95000.00, true, CURRENT_TIMESTAMP, 1),
('emp-005', 'person-005', 'DevOps Engineer', 'Engineering', 130000.00, true, CURRENT_TIMESTAMP, 1);
-- Note: person-004 (David Brown) is terminated, so no employee record

-- Badge Projections
INSERT INTO badge_projections (id, person_id, badge_number, access_level, clearance, active, last_updated, event_version) VALUES
('badge-001', 'person-001', 'B10001', 'RESTRICTED', 'SECRET', true, CURRENT_TIMESTAMP, 1),
('badge-002', 'person-002', 'B10002', 'ALL_ACCESS', 'TOP_SECRET', true, CURRENT_TIMESTAMP, 1),
('badge-003', 'person-003', 'B10003', 'STANDARD', 'CONFIDENTIAL', true, CURRENT_TIMESTAMP, 1),
('badge-005', 'person-005', 'B10005', 'RESTRICTED', 'SECRET', true, CURRENT_TIMESTAMP, 1);
-- Note: person-004 (David Brown) is terminated, so no active badge
