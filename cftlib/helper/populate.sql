/* psql postgresql://postgres:postgres@localhost:6432/nfd < backup.sql */
begin;
create schema mine;
create table mine.forenames(n text unique);
create table mine.surnames(n text unique);
create table mine.refs(id bigint unique);
\copy mine.forenames from forenames.txt
\copy mine.surnames from surnames.txt
\copy mine.refs from '../../../sql/aat/refs.txt'

with new_refs(ref) as (
select id from mine.refs limit 100000
)
insert into ccd.case_data(
  reference,
  last_modified,
  jurisdiction,
  case_type_id,
  state,
  data,
  security_classification,
  last_state_modified_date,
  supplementary_data
)
select 
x.ref,
now(),
jurisdiction,
case_type_id,
state,
data,
security_classification,
last_state_modified_date,
supplementary_data
from ccd.case_data, new_refs x;



with names (id, forename, surname) as (

   select row_number() OVER (ORDER BY random()), forenames.n, surnames.n from mine.forenames, mine.surnames
  limit 100000
),
cases(id, reference) as (
  select row_number() over (),
  reference 
  from ccd.case_data
)
/* select * from cases limit 5 */

UPDATE ccd.case_data
SET data = data
  || jsonb_build_object('applicant1FirstName', names.forename)
  || jsonb_build_object('applicant1LastName', names.surname)
FROM names, cases
WHERE names.id = cases.id and cases.reference = ccd.case_data.reference;

insert into multiples(lead_case_id, name) values((select max(reference) from ccd.case_data), 'Acme Inc vs Staff');
insert into multiple_members select m.multiple_id, reference as sub_case_id from ccd.case_data, multiples m;

insert into ccd.case_event(event_id, summary, description,
  user_id, case_reference, case_type_id, case_type_version, state_id,
  data, user_first_name, user_last_name,
  event_name, state_name, security_classification)
select
event_id, summary, description,
  user_id, cd.reference, cd.case_type_id, ce.case_type_version, state_id,
  cd.data, user_first_name, user_last_name,
  event_name, state_name, cd.security_classification
from ccd.case_event ce, ccd.case_data cd;

update ccd.case_data
set data = data ||
  jsonb_build_object('applicant1Address',
    jsonb_build_object(
      'County', 'Lead case County!',
      'Country', 'Lead case country!',
      'PostCode', 'LEADC4SE',
      'PostTown', 'London',
      'AddressLine1', 'London'
    )
  )
where reference in (select max(reference) from ccd.case_data);

commit;

vacuum full analyze;
