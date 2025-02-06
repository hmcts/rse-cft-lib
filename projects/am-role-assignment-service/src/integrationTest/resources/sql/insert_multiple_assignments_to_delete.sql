DELETE FROM role_assignment_history;
DELETE FROM role_assignment_request;
DELETE FROM role_assignment;
DELETE FROM actor_cache_control;


INSERT INTO public.role_assignment_request
(id, correlation_id, client_id, authenticated_user_id, assigner_id, request_type, status, process, reference, replace_existing, log, role_assignment_id, created)
VALUES('e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', '6c57c405-b7b2-4851-a78c-a634adcc8ce1', 'ccd_gw', '6eb64a6f-8273-4cdf-9b72-0a0ae4f9444f', '123e4567-e89b-42d3-a456-556642445678', 'CREATE', 'APPROVED', 'S-052', 'S-052', false, 'Request has been validated by rule : R02_request_validation', NULL, '2020-07-26 23:39:13.683');

INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0a', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM', '123e4567-e89b-42d3-a456-556642445612', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01 00:00:00.000', current_date+5, 'CREATED', 'S-052', 'S-052', '{"caseId": "1234567890123456", "region": "south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId": "003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123456 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', NULL, 10, '2020-07-26 23:39:13.726',ARRAY['dev']);
INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0a', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM', '123e4567-e89b-42d3-a456-556642445612', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01 00:00:00.000', current_date+5, 'APPROVED', 'S-052', 'S-052', '{"caseId": "1234567890123456", "region": "south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId": "003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123456 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', 'Requested Role has been approved by rule : R12_role_validation_for_case_pattern ', 10, '2020-07-26 23:39:13.823',ARRAY['tester']);
INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0a', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM', '123e4567-e89b-42d3-a456-556642445612', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01 00:00:00.000', current_date+5, 'LIVE', 'S-052', 'S-052', '{"caseId": "1234567890123456", "region": "south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId": "003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123456 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', 'Requested Role has been approved by rule : R12_role_validation_for_case_pattern ', 10, '2020-07-26 23:39:13.871',ARRAY['tester']);

INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0b', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM',
'123e4567-e89b-42d3-a456-556642445613', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01
00:00:00.000', current_date+5, 'CREATED', 'S-052', 'S-052', '{"caseId": "1234567890123457", "region":
"south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId":
"003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123457 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', NULL, 10, '2020-07-26 23:39:13.726',ARRAY['dev']);
INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0b', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM',
'123e4567-e89b-42d3-a456-556642445613', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01
00:00:00.000', current_date+5, 'APPROVED', 'S-052', 'S-052', '{"caseId": "1234567890123457", "region":
"south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId":
"003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123457 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', 'Requested Role has been approved by rule : R12_role_validation_for_case_pattern ', 10, '2020-07-26 23:39:13.823',ARRAY['tester']);
INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0b', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM',
'123e4567-e89b-42d3-a456-556642445613', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01
00:00:00.000', current_date+5, 'LIVE', 'S-052', 'S-052', '{"caseId": "1234567890123457", "region":
"south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId":
"003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123457 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', 'Requested Role has been approved by rule : R12_role_validation_for_case_pattern ', 10, '2020-07-26 23:39:13.871',ARRAY['tester']);

INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0c', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM',
'123e4567-e89b-42d3-a456-556642445614', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01
00:00:00.000', current_date+5, 'CREATED', 'S-052', 'S-052', '{"caseId": "1234567890123458", "region":
"south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId":
"003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123458 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', NULL, 10, '2020-07-26 23:39:13.726',ARRAY['dev']);
INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0c', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM',
'123e4567-e89b-42d3-a456-556642445614', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01
00:00:00.000', current_date+5, 'APPROVED', 'S-052', 'S-052', '{"caseId": "1234567890123458", "region":
"south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId":
"003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123458 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', 'Requested Role has been approved by rule : R12_role_validation_for_case_pattern ', 10, '2020-07-26 23:39:13.823',ARRAY['tester']);
INSERT INTO public.role_assignment_history
(id, request_id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, status, reference, process, "attributes", notes, log, status_sequence, created,authorisations)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0c', 'e1768fe8-f61d-4b56-99ec-9cc4c263b2c9', 'IDAM',
'123e4567-e89b-42d3-a456-556642445614', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01
00:00:00.000', current_date+5, 'LIVE', 'S-052', 'S-052', '{"caseId": "1234567890123458", "region":
"south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '[{"time": "2020-01-01T00:00", "userId":
"003352d0-e699-48bc-b6f5-5810411e60af", "comment": "Need Access to case number 1234567890123458 for a year"}, {"time": "2020-01-02T00:00", "userId": "52aa3810-af1f-11ea-b3de-0242ac130004", "comment": "Access granted for 3 months"}]', 'Requested Role has been approved by rule : R12_role_validation_for_case_pattern ', 10, '2020-07-26 23:39:13.871',ARRAY['tester']);

INSERT INTO public.role_assignment
(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, "attributes", created)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0a', 'IDAM', '123e4567-e89b-42d3-a456-556642445612', 'CASE', 'lead-judge', 'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01 00:00:00.000', current_date+5, '{"caseId": "1234567890123456", "region": "south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '2020-07-26 23:39:13.835');
INSERT INTO public.role_assignment
(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, "attributes", created)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0b', 'IDAM', '123e4567-e89b-42d3-a456-556642445613', 'CASE', 'lead-judge',
'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01 00:00:00.000', current_date+5, '{"caseId":
"1234567890123457", "region": "south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '2020-07-26 23:39:13.835');
INSERT INTO public.role_assignment
(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, "attributes", created)
VALUES('f7edb29d-e421-450c-be66-a10169b04f0c', 'IDAM', '123e4567-e89b-42d3-a456-556642445614', 'CASE', 'lead-judge',
'PUBLIC', 'SPECIFIC', 'JUDICIAL', false, '2021-08-01 00:00:00.000', current_date+5, '{"caseId":
"1234567890123458", "region": "south-east", "contractType": "SALARIED", "jurisdiction": "IA"}', '2020-07-26
23:39:13.835');

INSERT INTO public.actor_cache_control
(actor_id, etag, json_response)
VALUES('123e4567-e89b-42d3-a456-556642445612', 0, '[{"hour": 23, "nano": 835139000, "year": 2020, "month": "JULY", "minute": 39, "second": 13, "dayOfWeek": "SUNDAY", "dayOfYear": 208, "chronology": {"id": "ISO", "calendarType": "iso8601"}, "dayOfMonth": 26, "monthValue": 7}]');
INSERT INTO public.actor_cache_control
(actor_id, etag, json_response)
VALUES('123e4567-e89b-42d3-a456-556642445613', 0, '[{"hour": 23, "nano": 835139000, "year": 2020, "month": "JULY",
"minute": 39, "second": 13, "dayOfWeek": "SUNDAY", "dayOfYear": 208, "chronology": {"id": "ISO", "calendarType": "iso8601"}, "dayOfMonth": 26, "monthValue": 7}]');
INSERT INTO public.actor_cache_control
(actor_id, etag, json_response)
VALUES('123e4567-e89b-42d3-a456-556642445614', 0, '[{"hour": 23, "nano": 835139000, "year": 2020, "month": "JULY",
"minute": 39, "second": 13, "dayOfWeek": "SUNDAY", "dayOfYear": 208, "chronology": {"id": "ISO", "calendarType": "iso8601"}, "dayOfMonth": 26, "monthValue": 7}]');

INSERT INTO public.role_assignment
(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, attributes, created) VALUES
('cf89f230-0023-4bb6-b548-30da6a944173', 'IDAM', '6b36bfc6-bb21-11ea-b3de-0242ac130006', 'ORGANISATION','case-allocator', 'PUBLIC', 'STANDARD', 'JUDICIAL', true, '2021-01-01 12:00:00.000', current_date+5, '{"contractType": "SALARIED", "jurisdiction": "IA"}', '2020-06-25 12:30:41.166');
