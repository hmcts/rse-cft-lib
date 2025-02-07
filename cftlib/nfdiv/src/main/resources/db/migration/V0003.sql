drop schema if exists civil cascade;
create schema civil;


create table civil.parties(
                            party_id serial primary key,
                            forename text not null,
                            solicitor_id text
);


create table civil.claims(
                           reference bigint references ccd.case_data(reference) not null,
                           claim_id serial primary key,
                           description text not null,
                           amount_pence bigint not null check (amount_pence > 0)
);

create type civil.claim_role as enum(
  'claimant',
  'defendant'
);

create table civil.claim_members(
                                  claim_id integer references civil.claims(claim_id),
                                  party_id integer references civil.parties(party_id),
                                  role civil.claim_role ,
                                  primary key(claim_id, party_id)
);

create table civil.applications(
                                 party_id integer references civil.parties(party_id),
                                 claim_id integer references civil.claims(claim_id),
                                 reason text not null
);


create table civil.interested_parties(
                                       party_id integer references civil.parties(party_id),
                                       claim_id integer references civil.claims(claim_id),
                                       primary key(party_id, claim_id)
);

create view civil.pending_applications as
select party_id, claim_id, forename, description, reason from civil.applications
                                                                join civil.parties using (party_id)
                                                                join civil.claims using (claim_id);


create view civil.judge_claims as
select
  claim_id, reference, description, amount_pence,
  jsonb_agg(forename) filter (where role = 'claimant') claimants,
  jsonb_agg(forename) filter (where role = 'defendant') defendants
from
  civil.claims
  join civil.claim_members using (claim_id)
  join civil.parties using (party_id)
group by 1, 2, 3, 4;

create view civil.claims_by_client as
select
  solicitor_id,
  forename,
  role,
  reference,
  description,
  amount_pence
from
  civil.parties
  join civil.claim_members using (party_id)
  join civil.claims using (claim_id);

