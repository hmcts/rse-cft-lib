update role_assignment set attributes = "attributes" || '{"substantive":"Y"}' where
role_name = 'tribunal-caseworker' and role_category = 'LEGAL_OPERATIONS' AND role_type = 'ORGANISATION';

update role_assignment_history set attributes = "attributes" || '{"substantive":"Y"}' where
role_name = 'tribunal-caseworker' and role_category = 'LEGAL_OPERATIONS' AND role_type = 'ORGANISATION' and status ='LIVE';
