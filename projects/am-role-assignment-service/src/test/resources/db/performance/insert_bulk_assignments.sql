-- Postgres Function to insert bulk assignment records
drop type if exists roleCategories;
create type roleCategories as enum('PROFESSIONAL', 'JUDICIAL','LEGAL_OPERATIONS', 'CITIZEN');

drop type if exists professionalRoles;
create type professionalRoles as enum('[PETSOLICITOR]', '[RESPSOLICITOR]', '[BARRISTER]', '[CAFCASSSOLICITOR]',  '[EPSMANAGING]',  '[LABARRISTER]', '[LAMANAGING]', '[LASOLICITOR]',
   '[SOLICITOR]', '[SOLICITORA]', '[SOLICITORB]', '[SOLICITORC]', '[SOLICITORD]', '[SOLICITORE]', '[SOLICITORF]', '[SOLICITORG]', '[SOLICITORH]',
   '[SOLICITORI]', '[SOLICITORJ]', '[LEGALREPRESENTATIVE]', '[CREATOR]');

drop type if exists staffRoles;
create type staffRoles as enum('tribunal-caseworker', '[CREATOR]');

drop type if exists judgeRoles;
create type judgeRoles as enum('judge', '[CREATOR]');

--create function insert_bulk_assignments() RETURNS void AS $$
CREATE or replace FUNCTION insert_bulk_assignments(numberOfActors INT, numberOfRecords INT)
RETURNS void AS $$
declare
	assignmentId uuid;
	actorId uuid;
	number_of_assignments INT;
	professionalRole text;
	staffRole text;
	judgeRole text;
	roleCategory text;
	finalRole text;
begin
	--300000
	FOR counter IN 1..numberOfActors
loop
	--Generate random UUID for Actor
	actorId := uuid_in(md5(random()::text || clock_timestamp()::text)::cstring);
	begin
			--Generate a number between 1-30
			SELECT floor(random()*30)+1 into number_of_assignments;

			--Fetch random Role Category
			SELECT roleName into roleCategory FROM ( SELECT unnest(enum_range(NULL::roleCategories)) as roleName ) sub ORDER BY random() LIMIT 1;

			--Fetch random role from enum for Professional category
			SELECT roleName into professionalRole FROM ( SELECT unnest(enum_range(NULL::professionalRoles)) as roleName ) sub ORDER BY random() LIMIT 1;

			--Fetch random role from enum for Staff category
			SELECT roleName into staffRole FROM ( SELECT unnest(enum_range(NULL::staffRoles)) as roleName ) sub ORDER BY random() LIMIT 1;

			--Fetch random role from enum for Judicial category
			SELECT roleName into judgeRole FROM ( SELECT unnest(enum_range(NULL::judgeRoles)) as roleName ) sub ORDER BY random() LIMIT 1;

			IF roleCategory = 'PROFESSIONAL'
			THEN
				finalRole = professionalRole;
			END IF;

			IF roleCategory = 'CITIZEN'
			THEN
				finalRole = '[CREATOR]';
			END IF;

			IF roleCategory = 'JUDICIAL'
			THEN
				finalRole = judgeRole;
			END IF;

			IF roleCategory = 'LEGAL_OPERATIONS'
			THEN
				finalRole = staffRole;
			END IF;


			FOR counter IN 1..numberOfRecords
		    loop
		    	--Generate random UUID for assignmentId
		    	assignmentId := uuid_in(md5(random()::text || clock_timestamp()::text)::cstring);
		        INSERT INTO public.role_assignment
				(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, "attributes", created)
				VALUES(assignmentId, 'IDAM', actorId, 'CASE', finalRole, 'RESTRICTED', 'SPECIFIC', roleCategory, false, '2021-01-01 00:00:00.000', null, '{"caseId": "1234567890123456", "caseType": "Asylum", "jurisdiction": "IA"}', now());
		    end loop;
	end;
	-- Insert actor cache records
	INSERT INTO public.actor_cache_control
	(actor_id, etag, json_response)
	VALUES(actorId, 0, '[{}]');


END LOOP;
exception when others then
	begin
      raise exception 'Exception occured due to unknown issue';
	end;
end;
$$ LANGUAGE plpgsql;

select insert_bulk_assignments(10, 10);
--select insert_bulk_assignments(18, 1024);
--select insert_bulk_assignments(60, 512);
--select insert_bulk_assignments(268, 256);
--select insert_bulk_assignments(576, 128);
--select insert_bulk_assignments(1568, 64);
--select insert_bulk_assignments(4924, 32);
--select insert_bulk_assignments(13398, 16);
--select insert_bulk_assignments(25380, 8);
--select insert_bulk_assignments(46660, 4);
--select insert_bulk_assignments(221450, 2);
--select insert_bulk_assignments(1780686, 1);
