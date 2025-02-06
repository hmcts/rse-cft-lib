DELETE FROM role_assignment_history;
DELETE FROM role_assignment_request;

INSERT INTO public.role_assignment_request
(id, correlation_id, client_id, authenticated_user_id, assigner_id, request_type, status, process, reference, replace_existing, log, role_assignment_id, created)
VALUES('077dc12a-02ba-4238-87c3-803ca26b515f', 'd3bae3d0-cc26-43f2-b41c-a8451cdfd25f', 'ccd_gw', '772801eb-59b2-4f46-874b-3f34d0564923', '123e4567-e89b-42d3-a456-556642445678', 'CREATE', 'APPROVED', 'SPECIFIC', 'SPECIFIC', false, NULL, NULL, '2020-06-24 17:35:08.260');

INSERT INTO public.role_assignment_request
(id, correlation_id, client_id, authenticated_user_id, assigner_id, request_type, status, process, reference, replace_existing, log, role_assignment_id, created)
VALUES('340f6af0-bb36-4a9d-a67d-2f65f22be0cf', '85cd49d7-11b6-45a7-8b59-07196d2f3d24', 'ccd_gw', '772801eb-59b2-4f46-874b-3f34d0564923', '123e4567-e89b-42d3-a456-556642445678', 'CREATE', 'APPROVED', 'SPECIFIC', 'SPECIFIC', false, NULL, NULL, '2020-06-24 17:35:42.262');

INSERT INTO public.role_assignment_request
(id, correlation_id, client_id, authenticated_user_id, assigner_id, request_type, status, process, reference, replace_existing, log, role_assignment_id, created)
VALUES('e84480f9-26ce-4811-805a-9ffab72a9f78', 'ddd885c5-6ecb-4f2c-bc43-4b9fd1dc1eae', 'ccd_gw', '772801eb-59b2-4f46-874b-3f34d0564923', '123e4567-e89b-42d3-a456-556642445678', 'CREATE', 'APPROVED', 'SPECIFIC', 'SPECIFIC', false, NULL, NULL, '2020-06-25 12:30:40.901');

INSERT INTO public.role_assignment_request
(id, correlation_id, client_id, authenticated_user_id, assigner_id, request_type, status, process, reference, replace_existing, log, role_assignment_id, created)
VALUES('b320bfdc-c5ff-4064-9bce-50224e0a3af8', '8bbd03c3-56e8-4767-93a3-6ee8f57c3f00', 'ccd_gw', '772801eb-59b2-4f46-874b-3f34d0564923', '123e4567-e89b-42d3-a456-556642445678', 'CREATE', 'APPROVED', 'SPECIFIC', 'SPECIFIC', false, NULL, NULL, '2020-06-25 12:32:03.568');

INSERT INTO public.role_assignment_request
(id, correlation_id, client_id, authenticated_user_id, assigner_id, request_type, status, process, reference, replace_existing, log, role_assignment_id, created)
VALUES('ee1a0441-8126-48cc-b6c6-d44737cbd17d', '633a4436-8b5c-4710-867e-cf07ffc3370b', 'ccd_gw', '772801eb-59b2-4f46-874b-3f34d0564923', '123e4567-e89b-42d3-a456-556642445678', 'CREATE', 'APPROVED', 'SPECIFIC', 'SPECIFIC', false, NULL, NULL, '2020-06-25 12:32:06.991');

