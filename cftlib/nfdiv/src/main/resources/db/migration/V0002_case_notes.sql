drop table case_notes;
create table case_notes(
                         case_ref bigint references case_data(reference) ,
                         id bigserial,
                         date date not null,
                         note varchar(10000),
                         author varchar(200) not null,
                         primary key(case_id, id)
);

insert into case_notes(case_ref, id, date, note, author)
select
  reference,
  (note->>'id')::bigint,
  (note->'value'->>'date')::date,
  note->'value'->>'note',
  note->'value'->>'author'
from
  case_data,
  jsonb_array_elements(data->'notes') note;


select * from case_notes
where case_ref = 1730929821931214;
