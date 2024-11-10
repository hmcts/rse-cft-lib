create table es_queue (
  reference bigint references case_data(reference) primary key,
  id bigint references case_event(id)
);

create function public.add_to_es_queue() returns trigger
  language plpgsql
    as $$
begin
  insert into es_queue (reference, id)
  values (new.case_reference, new.id)
  on conflict (reference)
                do update set id = excluded.id
  where es_queue.id < excluded.id;
return new;
end $$;

create trigger after_case_event_insert
  after insert on case_event
  for each row
  execute function add_to_es_queue();
