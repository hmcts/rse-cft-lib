update role_assignment set attributes = "attributes" || '{"substantive":"N"}', created = now() where
role_name = 'fee-paid-judge' and role_category = 'JUDICIAL' AND role_type = 'ORGANISATION';


update role_assignment_history set attributes = "attributes" || '{"substantive":"N"}', created = now() where
role_name = 'fee-paid-judge' and role_category = 'JUDICIAL' AND role_type = 'ORGANISATION' and status ='LIVE';
