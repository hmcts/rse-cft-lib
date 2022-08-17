
with entries(val) as (select jsonb_array_elements(?::jsonb))
, rows(actor_id, val) as (
  select val->>'id', jsonb_array_elements(val->'roleAssignments') from entries
), clean as (
  -- Optionally clean existing role assignments if requested
  delete from role_assignment where ?::boolean and actor_id in (select actor_id from rows)
), actors as (
insert into role_assignment
  select
    gen_random_uuid() as id,
    'IDAM' as actor_id_type,
    actor_id,
    r."roleType",
    r."roleName",
    r.classification,
    r."grantType",
    r."roleCategory",
    r."readOnly",
    now() at time zone 'utc' as begin_time,
    now() at time zone 'utc' + interval '10 years' as end_time,
    r.attributes,
    now() at time zone 'utc' as created,
    r.authorisations
  from rows,
    jsonb_to_record(val) as r(
      "roleType" text,
      "roleName" text,
      "grantType" text,
      "roleCategory" text,
      "classification" text,
      "readOnly" boolean,
      attributes jsonb,
      authorisations text[]
    )
  returning actor_id
)
insert into actor_cache_control
  select distinct actor_id, 1, '{}'::jsonb  from actors
on conflict (actor_id) do update set etag = actor_cache_control.etag + 1
returning *
