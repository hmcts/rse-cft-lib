update role_assignment set attributes = "attributes" || '{"substantive":"Y"}' where role_type = 'CASE';
update role_assignment_history set attributes = "attributes" || '{"substantive":"Y"}' where role_type = 'CASE';

update role_assignment set attributes = "attributes" || '{"substantive":"N"}' where role_type = 'CASE' and
role_name in ('tribunal-caseworker', '[CREATOR]', '[CLAIMANT]', '[DEFENDANT]', '[APPLICANTTWO]');
update role_assignment_history set attributes = "attributes" || '{"substantive":"N"}' where role_type = 'CASE' and
role_name in ('tribunal-caseworker', '[CREATOR]', '[CLAIMANT]', '[DEFENDANT]', '[APPLICANTTWO]');
