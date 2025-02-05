DELETE FROM role_assignment;

INSERT INTO public.role_assignment
(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, attributes, created) VALUES
('6b36bfc6-bb21-11ea-b3de-0242ac130006', 'IDAM', '123e4567-e89b-42d3-a456-556642445612', 'ORGANISATION','leadership-judge', 'PUBLIC', 'STANDARD', 'JUDICIAL', false, '2021-01-01 12:00:00.000', current_date+5, '{"contractType": "SALARIED", "jurisdiction": "IA"}', '2020-06-24 17:35:08.546'),
('cf89f230-0023-4bb6-b548-30da6a944173', 'IDAM', '6b36bfc6-bb21-11ea-b3de-0242ac130006', 'ORGANISATION','case-allocator', 'PUBLIC', 'STANDARD', 'JUDICIAL', true, '2021-01-01 12:00:00.000', current_date+5, '{"contractType": "SALARIED", "jurisdiction": "IA"}', '2020-06-25 12:30:41.166'),
('6b36bfc6-bb21-11ea-b3de-0242ac130007', 'IDAM', '123e4567-e89b-42d3-a456-556642445612', 'ORGANISATION','senior-judge', 'PUBLIC', 'STANDARD', 'JUDICIAL', false, '2021-01-01 12:00:00.000', current_date+5, '{"contractType": "SALARIED", "jurisdiction": "IA"}', '2020-06-24 17:35:08.546');
