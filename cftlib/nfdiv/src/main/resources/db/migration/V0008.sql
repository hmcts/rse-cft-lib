create table case_event_audit (
                                id bigserial primary key,
                                case_event_id bigint not null references case_event(id),
                                user_id uuid not null,
                                data jsonb not null
);

create function audit_case_event_changes()
  returns trigger as $$
begin
insert into case_event_audit(case_event_id, user_id, data)
select new.id, current_setting('ccd.user_idam_id')::uuid, new.data;
return new;
end;
$$ language plpgsql;

create trigger audit_case_event_changes
  after update of data on case_event
  for each row
  execute function audit_case_event_changes();

