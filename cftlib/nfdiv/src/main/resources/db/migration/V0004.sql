
create table multiples (
  multiple_id serial primary key,
  lead_case_id bigint not null unique,
  name text not null
);

create table multiple_members(
    multiple_id bigint not null references multiples(multiple_id) on delete cascade,
    sub_case_id bigint not null references case_data(reference) on delete cascade,
    unique (multiple_id, sub_case_id)
);

alter table multiples
    add foreign key (multiple_id, lead_case_id)
    references multiple_members(multiple_id, sub_case_id)
    deferrable initially deferred;

create view sub_cases as (
  select
  m.name,
  lead_case_id,
  sub_case_id,
  cd.last_modified,
  cd.data->>'applicant1FirstName' applicant1FirstName,
  cd.data->>'applicant1LastName' applicant1LastName,
  cd.data->>'applicant2FirstName' applicant2FirstName,
  cd.data->>'applicant2LastName' applicant2LastName
  from
    multiples m
    join multiple_members s using (multiple_id)
    join case_data cd on cd.reference = s.sub_case_id
  order by cd.last_modified desc
);





